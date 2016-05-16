package edu.uw.easysrl.syntax.evaluation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.CCGBankDependencies.DependencyParse;
import edu.uw.easysrl.corpora.CCGBankDependencies.Partition;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.dependencies.DependencyGenerator;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.syntax.training.PipelineTrainer.LabelClassifier;
import edu.uw.easysrl.util.Util;

public class CCGBankEvaluation {
	static class ErrorAnalysis {
		Multiset<String> precisionErrors = HashMultiset.create();
		Multiset<String> recallErrors = HashMultiset.create();
		Multiset<String> correct = HashMultiset.create();
		Map<String, Results> perRelationResults = new HashMap<>();
		Map<Integer, Results> lengthBinToResults = new HashMap<>();
		{
			for (int i = 0; i < 14; i++) {
				lengthBinToResults.put(i, new Results());
			}
		}
	}

	private final static Set<String> filter = new HashSet<>(Arrays.asList(

	"be:(S[b]\\NP)/(S[pss]\\NP).1", "be:(S[b]\\NP)/(S[adj]\\NP).1", "been:(S[pt]\\NP)/(S[pss]\\NP).1",
			"have:(S[b]\\NP)/(S[pt]\\NP).1", "been:(S[pt]\\NP)/(S[ng]\\NP).1", "been:(S[pt]\\NP)/(S[adj]\\NP).1",
			"going:(S[ng]\\NP)/(S[to]\\NP).1", "have:(S[b]\\NP)/(S[to]\\NP).1", "be:(S[b]\\NP)/(S[ng]\\NP).1"

	));

	// private final static Set<String> filter = new HashSet<>();

	public final static LabelClassifier dummyLabelClassifier = new LabelClassifier(null) {
		@Override
		public SRLLabel classify(final UnlabelledDependency dep, final List<InputWord> sentence) {
			return SRLFrame.NONE;
		}
	};

	public static void main(final String[] args) throws IOException {
		final String pipelineFolder = Util.getHomeFolder() + "/Downloads/cnn/models/model_tritrain_big";

		final SRLParser pipeline = SRLParser.wrapperOf(new ParserAStar.Builder(new File(pipelineFolder))
				.supertaggerBeam(0.000001).build());

		System.out.println(evaluate(pipeline, Partition.DEV));
	}

	private static void compareParses(final DependencyParse bestParse, final SyntaxTreeNode syntaxTreeNode) {
		final List<String> words = bestParse.getLeaves().stream().map(x -> x.getWord()).collect(Collectors.toList());
		final List<Category> predictedTags = bestParse.getLeaves().stream().map(x -> x.getCategory())
				.collect(Collectors.toList());
		final List<Category> goldTags = syntaxTreeNode.getLeaves().stream().map(x -> x.getCategory())
				.collect(Collectors.toList());
		for (int i = 0; i < predictedTags.size(); i++) {
			System.out.print(words.get(i));
			if (!predictedTags.get(i).equals(goldTags.get(i))) {
				System.out.print("|" + goldTags.get(i) + "|" + predictedTags.get(i));
			}

			System.out.print(" ");
		}
		System.out.println();
	}

	public static Results evaluate(final SRLParser parser, final Partition partition) throws IOException {

		// final List<Set<ResolvedDependency>> expected = loadCandC(new File(
		// "/Users/mike/Google Drive/git/easyccg/training/experiments/wsj00/easysrl.deps"));

		// final FileWriter fout = new FileWriter("/Users/mike/Google Drive/git/easyccg/training/experiments/wsj"
		// + (isDev ? "00" : "23") + "/easysrl.auto", false);
		final Results results = new Results();
		final Results resultsWithCorrectSupertags = new Results();
		final Results resultsWithWrongSupertags = new Results();

		final Results unlabelled = new Results();
		int allCorrectTags = 0;
		int notAllCorrectTags = 0;
		int correctTags = 0;
		int numTags = 0;
		final ErrorAnalysis errorAnalysis = new ErrorAnalysis();
		final ErrorAnalysis errorAnalysisUnlabelled = new ErrorAnalysis();
		final Table<Category, Category, Integer> goldToPredictedToCount = HashBasedTable.create();

		final ErrorAnalysis errorAnalysisWithGoldCats = new ErrorAnalysis();

		final DependencyGenerator dependencyGenerator = new DependencyGenerator(
				Util.getFile("~/Downloads/cnn/models/model_ccgbank"));

		final Multiset<String> validDeps = getValidDeps();

		// final Iterator<String> taggedSentences = Util.readFile(tagsFile).iterator();
		final PrintWriter output = new PrintWriter(new FileWriter("/tmp/easysrl.auto"));
		int id = 0;
		int allCorrect = 0;
		int numParsed = 0;

		final List<DependencyParse> corpus = CCGBankDependencies.loadCorpus(ParallelCorpusReader.CCGREBANK, partition);
		final Stopwatch watch = Stopwatch.createStarted();
		for (final DependencyParse gold : corpus) {

			final Set<ResolvedDependency> expectedForParse = null; // isDev ? expected.get(id) : Collections.emptySet();
			id++;
			// System.err.println(gold.getWords());

			if (gold.getWords().size() > 4 && gold.getWords().get(4).word.equals("McGraw-Hill")) {
				Util.debugHook();
			}

			// final InputToParser input = reader.readInput(taggedSentences.next());
			// final List<Scored<SyntaxTreeNode>> parses = parser.doParsing(input);
			try {
				final List<CCGandSRLparse> parses = parser.parseTokens(gold.getWords());

				output.println(ParsePrinter.CCGBANK_PRINTER.print(parses == null ? null : parses.get(0).getCcgParse(),
						id));
				Results r = null;
				final Set<ResolvedDependency> evalDeps;
				final List<String> words = gold.getWords().stream().map(x -> x.word).collect(Collectors.toList());

				final Set<ResolvedDependency> goldDeps = asResolvedDependencies(gold.getDependencies());
				if (parses != null && parses.size() > 0) {

					SyntaxTreeNode bestParse = null;
					final Set<Set<ResolvedDependency>> uniqueParses = new HashSet<>();
					// System.out.println();
					for (final CCGandSRLparse parse : parses) {

						Preconditions.checkState(words.size() == parse.getCcgParse().getLength());
						final Set<UnlabelledDependency> deps = new HashSet<>();
						dependencyGenerator.generateDependencies(parse.getCcgParse(), deps);
						// extractDependencies(parse.getCcgParse(), deps);
						Set<ResolvedDependency> forParse = convertDeps(gold.getWords(), deps);

						forParse = forParse.stream().filter(x -> x.getHead() != x.getArgument())
								.collect(Collectors.toSet());

						forParse = forParse.stream()
								.filter(x -> validDeps.count(x.getCategory().toString() + x.getArgNumber()) >= 10)
								.collect(Collectors.toSet());

						final Results resultsForParse = evaluate(forParse, goldDeps, errorAnalysis, words);

						uniqueParses.add(forParse);

						unlabelled.add(evaluate(unlabel(forParse), unlabel(goldDeps), errorAnalysisUnlabelled, words));

						// System.out.print(Util.twoDP(100.0 * resultsForParse.getF1()) + " ");
						if (r == null || resultsForParse.getF1() > r.getF1()) {
							r = resultsForParse;
							bestParse = parse.getCcgParse();
						}

						break; // FIXME
					}

					if (r.getF1() == 1.0) {
						allCorrect++;
					}

					numParsed++;
					// System.out.println(uniqueParses.size() + "/" + parses.size() + " "
					// + Util.twoDP(100.0 * allCorrect / numParsed));

					// Error analysis on Oracle parse
					final Set<UnlabelledDependency> deps = new HashSet<>();
					dependencyGenerator.generateDependencies(bestParse, deps);

					// extractDependencies(bestParse, deps);
					final Set<ResolvedDependency> forParse = convertDeps(gold.getWords(), deps);
					final List<Category> predictedTags = bestParse.getLeaves().stream().map(x -> x.getCategory())
							.collect(Collectors.toList());
					final List<Category> goldTags = gold.getLeaves().stream().map(x -> x.getCategory())
							.collect(Collectors.toList());
					for (int i = 0; i < predictedTags.size(); i++) {
						if (predictedTags.get(i).equals(goldTags.get(i))) {
							correctTags++;
						}
						numTags++;

						final Integer current = goldToPredictedToCount.get(goldTags.get(i), predictedTags.get(i));
						goldToPredictedToCount.put(goldTags.get(i), predictedTags.get(i), current == null ? 1
								: current + 1);
					}

					if (predictedTags.equals(goldTags)) {
						allCorrectTags++;
						resultsWithCorrectSupertags.add(r);

						evaluate(forParse, goldDeps, errorAnalysisWithGoldCats, words);
					} else {
						notAllCorrectTags++;
						resultsWithWrongSupertags.add(r);
					}
				} else {

					System.err.println("Failed to parse: " + words);
					evalDeps = new HashSet<>();
					if (partition != Partition.TEST) {

						continue;

					} else {
						r = evaluate(Collections.emptySet(), goldDeps, errorAnalysis, words);
					}
				}

				results.add(r);

			} catch (final Throwable t) {
				t.printStackTrace();
			}
		}

		watch.stop();

		System.err.println();
		for (final java.util.Map.Entry<String, Results> relation : errorAnalysis.perRelationResults.entrySet().stream()
				.sorted((e1, e2) -> e2.getValue().getFrequency() - e1.getValue().getFrequency())
				.collect(Collectors.toList())) {
			if (relation.getValue().getFrequency() > 50) {
				System.err.println(relation.getKey() + "\t" + Util.twoDP(100.0 * relation.getValue().getPrecision())
						+ "\t" + Util.twoDP(100.0 * relation.getValue().getRecall()) + "\t"
						+ Util.twoDP(100.0 * relation.getValue().getF1()));
			}
		}

		for (final Cell<Category, Category, Integer> entry : goldToPredictedToCount.cellSet().stream()
				.sorted((x, y) -> Integer.compare(x.getValue(), y.getValue())).collect(Collectors.toList())) {
			if (entry.getRowKey() != entry.getColumnKey() && entry.getValue() > 10) {
				System.out.println(entry);
			}
		}

		System.out.println("(0," + Util.twoDP(100.0 * errorAnalysis.lengthBinToResults.get(0).getF1()) + ")");
		for (int j = 0; j < 14; j++) {
			System.out.println("(" + 5 * (j + 1) + ","
					+ Util.twoDP(100.0 * errorAnalysis.lengthBinToResults.get(j).getF1()) + ")");
		}

		output.close();

		System.out.println("With correct tags:    " + Util.twoDP(100.0 * resultsWithCorrectSupertags.getF1()) + " ("
				+ Util.twoDP(100.0 * allCorrectTags / (allCorrectTags + notAllCorrectTags)) + ")");
		System.out.println("With wrong tags:      " + Util.twoDP(100.0 * resultsWithWrongSupertags.getF1()) + " ("
				+ notAllCorrectTags + ")");
		System.out.println("Supertagging results: " + Util.twoDP(100.0 * correctTags / numTags));
		System.out.println("Unlabelled");
		System.out.println(unlabelled);
		System.out.println("Labelled");
		System.out.println(results);
		// fout.close();
		System.out.println(watch.elapsed(TimeUnit.SECONDS) + "s");
		return results;

	}

	public static Multiset<String> getValidDeps() throws IOException {
		final Multiset<String> validDeps = HashMultiset.create();
		for (final DependencyParse gold : CCGBankDependencies.loadCorpus(ParallelCorpusReader.CCGREBANK,
				Partition.TRAIN)) {
			for (final ResolvedDependency dep : asResolvedDependencies(gold.getDependencies())) {
				validDeps.add(dep.getCategory().toString() + dep.getArgNumber());
			}
		}
		return validDeps;
	}

	public static Set<ResolvedDependency> convertDeps(final List<InputWord> words, final Set<UnlabelledDependency> deps) {
		final Set<ResolvedDependency> forParse = new HashSet<>();
		for (final UnlabelledDependency dep : deps) {
			for (final Integer argument : dep.getArguments()) {
				if (!filter(words.get(dep.getHead()).word, dep.getCategory(), dep.getArgNumber())
						&& argument != dep.getHead()) {
					forParse.add(new ResolvedDependency(dep.getHead(), dep.getCategory(), dep.getArgNumber(), argument,
							SRLFrame.NONE, null));

				}

			}
		}
		return forParse;
	}

	private static Set<ResolvedDependency> unlabel(final Set<ResolvedDependency> deps) {
		return deps
				.stream()
				.map(x -> new ResolvedDependency(x.getHead(), Category.N, 0, x.getArgumentIndex(), SRLFrame.NONE, null))
				.collect(Collectors.toSet());
	}

	public static boolean filter(final String word, final Category category, final int argumentNumber) {

		return filter.contains(category + "." + argumentNumber)
				|| filter.contains(word + ":" + category + "." + argumentNumber);
	}

	public static Set<ResolvedDependency> asResolvedDependencies(final Collection<CCGBankDependency> dependencies) {
		final Set<ResolvedDependency> result = new HashSet<>();
		for (final CCGBankDependency dep : dependencies) {
			result.add(new ResolvedDependency(dep.getSentencePositionOfPredicate(), dep.getCategory(), dep
					.getArgNumber(), dep.getSentencePositionOfArgument(), SRLFrame.NONE, null));
		}

		return result;
	}

	private static Results evaluate(final Set<ResolvedDependency> predicted, final Set<ResolvedDependency> gold,
			final ErrorAnalysis ea, final List<String> words) {

		final Set<ResolvedDependency> correctDeps = Sets.intersection(predicted, gold);

		for (final ResolvedDependency dep : Sets.union(predicted, gold)) {
			final String key = dep.getCategory() + "." + dep.getArgNumber();
			Results results = ea.perRelationResults.get(key);
			if (results == null) {
				results = new Results();
				ea.perRelationResults.put(key, results);
			}
			final Results resultsForDep = new Results(predicted.contains(dep) ? 1 : 0, correctDeps.contains(dep) ? 1
					: 0, gold.contains(dep) ? 1 : 0);
			results.add(resultsForDep);

			ea.lengthBinToResults.get(Math.abs(dep.getOffset()) / 5).add(resultsForDep);
		}

		// System.out.println(predicted.size());
		// for (final ResolvedDependency dep : predicted) {
		// System.out.println(words.get(dep.getHead()) + "\t" + dep.getCategory() + "." + dep.getArgNumber() + "\t"
		// + words.get(dep.getArgumentIndex()));
		// }
		// System.out.println();

		addCounts(Sets.difference(predicted, gold), ea.precisionErrors, words);
		addCounts(Sets.difference(gold, predicted), ea.recallErrors, words);
		addCounts(correctDeps, ea.correct, words);

		return new Results(predicted.size(), correctDeps.size(), gold.size());
	}

	private static void addCounts(final Set<ResolvedDependency> deps, final Multiset<String> precisionErrors,
			final List<String> words) {
		for (final ResolvedDependency precisionError : deps) {
			precisionErrors.add(precisionError.getCategory() + "." + precisionError.getArgNumber());
			precisionErrors.add(words.get(precisionError.getHead()) + ":" + precisionError.getCategory() + "."
					+ precisionError.getArgNumber());
		}
	}

	public static void extractDependencies(final SyntaxTreeNode parse, final Collection<UnlabelledDependency> deps) {

		if (parse.getChildren().size() == 2) {

			deps.addAll(parse.getResolvedUnlabelledDependencies());
		}
		for (final SyntaxTreeNode child : parse.getChildren()) {
			extractDependencies(child, deps);
		}
	}
}
