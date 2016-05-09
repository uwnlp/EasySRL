package edu.uw.easysrl.syntax.model;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.CCGBankDependencies.DependencyParse;
import edu.uw.easysrl.corpora.CCGBankDependencies.Partition;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.Tagger.ScoredCategory;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Scored;

public class OracleDependenciesModel extends Model {
	private final DependencyParse goldDeps;
	private final Collection<Category> categories;
	private final Collection<ResolvedDependency> gold;
	private final List<String> words;
	private final double globalUpperBound;
	private final List<List<ScoredCategory>> tags;
	private final static double catScore = 0.1; // 0.001
	final static Multiset<String> validDeps;
	static {
		try {
			validDeps = CCGBankEvaluation.getValidDeps();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected OracleDependenciesModel(final InputToParser input, final List<List<ScoredCategory>> tags,
			final Collection<Category> categories) {
		super(input.getInputWords().size());
		words = input.getInputWords().stream().map(x -> x.word).collect(Collectors.toList());
		goldDeps = ((InputToParserWithGoldDeps) input).dependencyParse;
		gold = goldDeps.getResolvedDependencies();
		globalUpperBound = gold.size() + catScore * input.getInputWords().size();
		this.categories = ImmutableSet.copyOf(categories);
		this.tags = tags;
		computeOutsideProbabilities();

	}

	public static void main(final String[] args) throws IOException {
		final String pipelineFolder = Util.getHomeFolder() + "/Downloads/cnn/models/model_ccgbank";
		final Set<Category> categories = ImmutableSet.copyOf(TaggerEmbeddings.loadCategories(new File(pipelineFolder,
				"categories")));
		final Tagger tagger = Tagger.make(Util.getFile("~/Downloads/cnn/models/model_ccgbank"), 0.001, 50, null);// 0.1
		final OracleDependenciesModelFactory modelFactory = new OracleDependenciesModelFactory(tagger, categories);

		final File output = new File(Util.getHomeFolder() + "/Downloads/cnn/ccgbank_oracle0.01/train.stagged");
		final ParserAStar parser = new ParserAStar(modelFactory, 250, 1, Arrays.asList(Category.valueOf("S[dcl]"),
				Category.valueOf("S[q]"), Category.valueOf("S[wq]"), Category.valueOf("NP"),
				Category.valueOf("S[b]\\NP"), Category.valueOf("NP[nb]")), new File(pipelineFolder), 100000);
		int coverage = 0;
		int sentences = 0;
		int correctTags = 0;
		int totalTags = 0;
		final PrintWriter out = new PrintWriter(output);
		final Results results = new Results();
		for (final DependencyParse gold : CCGBankDependencies.loadCorpus(

				new File("/Users/mike/Documents/git/easyccg/testfiles/ccgbank"), Partition.TRAIN)) {

			final List<Scored<SyntaxTreeNode>> parses = parser.doParsing(new InputToParserWithGoldDeps(gold.getWords(),
					null, null, false, gold));

			if (sentences % 100 == 0) {
				System.out.println("Coverage:     " + Util.twoDP(100.0 * coverage / sentences));
				System.out.println("Supertagging: " + Util.twoDP(100.0 * correctTags / totalTags));
				System.out.println(results);
			}
			sentences++;
			if (parses == null) {
				continue;
			}
			coverage++;
			final SyntaxTreeNode parse = parses.get(0).getObject();
			int tagAcc = 0;
			for (int i = 0; i < gold.getWords().size(); i++) {
				if (parse.getLeaves().get(i).getCategory() == gold.getLeaves().get(i).getCategory()) {
					tagAcc++;
				}
			}

			final Set<UnlabelledDependency> deps2 = new HashSet<>();
			CCGBankEvaluation.extractDependencies(parse, deps2);
			Set<ResolvedDependency> deps = CCGBankEvaluation.convertDeps(gold.getWords(), deps2);
			final Set<ResolvedDependency> goldDeps = ImmutableSet.copyOf(gold.getResolvedDependencies());

			deps = deps.stream().filter(x -> validDeps.count(x.getCategory().toString() + x.getArgNumber()) >= 10)
					.collect(Collectors.toSet());

			results.add(new Results(deps.size(), com.google.common.collect.Sets.intersection(deps, goldDeps).size(),
					goldDeps.size()));

			correctTags += tagAcc;
			totalTags += gold.getWords().size();
			if (tagAcc < gold.getWords().size()) {
				System.out.println(tagAcc + "/" + gold.getWords().size());
				// System.out.println(ParsePrinter.SUPERTAG_PRINTER.print(parses.get(0).getObject(), 0));
				for (int i = 0; i < gold.getWords().size(); i++) {
					System.out.print(gold.getWords().get(i).word);
					if (parse.getLeaves().get(i).getCategory() != gold.getLeaves().get(i).getCategory()) {
						System.out.print("|" + gold.getLeaves().get(i).getCategory() + "|"
								+ parse.getLeaves().get(i).getCategory());
					}

					System.out.print(" ");
				}
				System.out.println();
			}

			out.println(ParsePrinter.SUPERTAG_PRINTER.print(parses.get(0).getObject(), 0));

		}

		out.close();
	}

	private static class OracleDependenciesModelFactory extends ModelFactory {
		private final Collection<Category> cats;
		private final Tagger tagger;

		OracleDependenciesModelFactory(final Tagger tagger, final Collection<Category> cats) {
			this.cats = cats;
			this.tagger = tagger;
		}

		@Override
		public Model make(final InputToParser sentence) {
			return new OracleDependenciesModel(sentence, tagger.tag(sentence.getInputWords()), cats);
		}

		@Override
		public Collection<Category> getLexicalCategories() {
			return cats;
		}

		@Override
		public boolean isUsingDependencies() {
			return true;
		}
	}

	private static class InputToParserWithGoldDeps extends InputToParser {
		public InputToParserWithGoldDeps(final List<InputWord> words, final List<Category> goldCategories,
				final List<List<ScoredCategory>> inputSupertags, final boolean isAlreadyTagged,
				final DependencyParse parse) {
			super(words, goldCategories, inputSupertags, isAlreadyTagged);
			this.dependencyParse = parse;
		}

		private final DependencyParse dependencyParse;

	}

	@Override
	public double getUpperBoundForWord(final int index) {
		final Collection<CCGBankDependency> deps = goldDeps.getDependencies(index);
		return catScore + (deps == null ? 0 : deps.size());
	}

	@Override
	public void buildAgenda(final Agenda queue, final List<InputWord> words) {
		int i = 0;
		for (final InputWord w : words) {

			double max = 0.0;
			final Category goldCat = goldDeps.getLeaves().get(i).getCategory();
			for (final ScoredCategory scoredCat : tags.get(i)) {
				final Category c = scoredCat.getCategory();
				int numberOfDeps = 0;
				for (int j = 1; j <= c.getNumberOfArguments(); j++) {
					if (!CCGBankEvaluation.filter(this.words.get(i), c, j)) {
						numberOfDeps++;
					}
				}
				max = Math.max(max, scoredCat.getScore());
				if (c != goldCat) {
					// continue;
				} else {
					continue;
				}
				final int upperBound = c == goldCat ? goldDeps.getDependencies(i).size() : -numberOfDeps;
				queue.add(new AgendaItem(new SyntaxTreeNodeLeaf(w.word, w.pos, w.ner, c, i), scoredCat.getScore()
						* catScore, getOutsideUpperBound(i, i + 1) + upperBound, i, 1, true));

			}

			if (categories.contains(goldCat)) {
				queue.add(new AgendaItem(new SyntaxTreeNodeLeaf(w.word, w.pos, w.ner, goldCat, i), max * catScore,
						getOutsideUpperBound(i, i + 1) + goldDeps.getDependencies(i).size(), i, 1, true));
			}

			i++;
		}

	}

	@Override
	public AgendaItem combineNodes(final AgendaItem leftChild, final AgendaItem rightChild, final SyntaxTreeNode node) {
		final int score = scoreNode(node);

		if (rightChild.getParse().getHead().getWord().equals("around")) {
			Util.debugHook();
		}

		int expectedDeps = 0;
		for (final ResolvedDependency dep : gold) {
			if (expected(leftChild, rightChild, dep) || expected(rightChild, leftChild, dep)) {
				expectedDeps++;
			}
		}

		final double insideScore = leftChild.getInsideScore() + rightChild.getInsideScore() + score;
		return new AgendaItem(node, insideScore, leftChild.outsideScoreUpperbound + rightChild.outsideScoreUpperbound
				- globalUpperBound - expectedDeps, leftChild.getStartOfSpan(), leftChild.getSpanLength()
				+ rightChild.getSpanLength(), true);
	}

	private boolean expected(final AgendaItem leftChild, final AgendaItem rightChild, final ResolvedDependency dep) {
		return dep.getHead() >= rightChild.getStartOfSpan()
				&& dep.getHead() < rightChild.getStartOfSpan() + rightChild.getSpanLength()
				&& dep.getArgumentIndex() >= leftChild.getStartOfSpan()
				&& dep.getArgumentIndex() < leftChild.getStartOfSpan() + leftChild.getSpanLength();
	}

	private int scoreNode(final SyntaxTreeNode node) {
		int score = 0;
		for (final UnlabelledDependency dep : node.getResolvedUnlabelledDependencies()) {
			if (!CCGBankEvaluation.filter(words.get(dep.getHead()), dep.getCategory(), dep.getArgNumber())) {
				for (final int arg : dep.getArguments()) {
					if (gold.contains(new ResolvedDependency(dep.getHead(), dep.getCategory(), dep.getArgNumber(), arg,
							SRLFrame.NONE, null))) {
						score++;
					} else {
						score--;
					}
				}
			}
		}
		return score;
	}

	@Override
	public AgendaItem unary(final AgendaItem child, final SyntaxTreeNode result, final UnaryRule rule) {
		return new AgendaItem(result, child.getInsideScore() + scoreNode(result), child.outsideScoreUpperbound,
				child.startOfSpan, child.spanLength, true);
	}

}
