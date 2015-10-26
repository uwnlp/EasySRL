package edu.uw.easysrl.syntax.evaluation;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.CCGBankDependencies.DependencyParse;
import edu.uw.easysrl.corpora.CCGBankDependencies.Partition;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.DependencyStructure.UnlabelledDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.util.Util;

public class CCGBankEvaluation {

	// Dependencies that are ignored by CCGBank, or only very rarely right
	// on dev data. C&C eval script does osmething similar.
	private final static Set<String> filter = new HashSet<>(Arrays.asList("((S\\NP)\\(S\\NP))/NP.1",
			"((S\\NP)/(S\\NP))/NP.1", "(N/N)/(N/N).1", "(N/N)\\(N/N).1", "(S\\NP)\\(S\\NP).1",
			"(S[to]\\NP)/(S[b]\\NP).1",
			"(S\\NP)/(S\\NP).1",
			"NP/(N/PP).1",
			"n't:(S\\NP)\\(S\\NP).1",
			"((S\\NP)\\(S\\NP))/S[dcl].1",
			"((S\\NP)\\(S\\NP))/((S\\NP)\\(S\\NP)).1",
			"((S\\NP)\\(S\\NP))/((S\\NP)\\(S\\NP)).2",
			"((S\\NP)/(S\\NP))/((S\\NP)/(S\\NP)).1",
			"(S[adj]\\NP)/(S[adj]\\NP).1",
			"((S\\NP)\\(S\\NP))/PP.1",
			"((S\\NP)\\(S\\NP))/(S[ng]\\NP).1",
			"((S\\NP)\\(S\\NP))/(S[b]\\NP).1",
			"((S\\NP)\\(S\\NP))/(S[b]\\NP).2",
			"((S\\NP)\\(S\\NP))/(S[b]\\NP).3", // Does help for
			// unlabelled deps
			"((S\\NP)\\(S\\NP))/N.1", "more:S[adj]\\NP.1", "(S/S)/(S/S).1", "((S[adj]\\NP)\\(S[adj]\\NP))/NP.1",
			"conj.1", "than:((N/N)/(N/N))\\(S[adj]\\NP).1", "((N/N)/(N/N))\\(S[adj]\\NP).1",
			"((S\\NP)\\(S\\NP))/N[num].1", "((S\\NP)/(S\\NP))/((S\\NP)/(S\\NP)).2", "((N/N)/(N/N))/((N/N)/(N/N)).1",
			"((N/N)/(N/N))/((N/N)/(N/N)).2", "(NP\\NP)/(NP\\NP).1", "(NP/NP)/(NP/NP).1", "S[dcl]/NP.1",
			"which:((N\\N)/S[dcl])\\((N\\N)/NP).3", "((S\\NP)\\(S\\NP))\\NP.1", "(N\\N)/(N\\N).1",
			"((N/PP)\\(N/PP))/NP.1"

	));

	public static Results evaluate(final SRLParser parser, final boolean isDev) throws IOException {

		final Results results = new Results();

		final Multiset<String> precisionErrors = HashMultiset.create();
		final Multiset<String> correct = HashMultiset.create();
		final Map<String, Results> perRelationResults = new HashMap<>();

		for (final DependencyParse gold : CCGBankDependencies.loadCorpus(ParallelCorpusReader.CCGREBANK,
				isDev ? Partition.DEV : Partition.TEST)) {

			System.err.println(gold.getWords());

			final List<CCGandSRLparse> parses = parser.parseTokens(gold.getWords());

			Set<ResolvedDependency> evalDeps;

			if (parses != null && parses.size() > 0) {
				final Set<UnlabelledDependency> deps = new HashSet<>();
				extractDependencies(parses.get(0).getCcgParse(), deps);
				evalDeps = new HashSet<>();

				for (final UnlabelledDependency dep : deps) {
					for (final Integer argument : dep.getArguments()) {
						if (!filter(gold.getWords().get(dep.getHead()).word, dep.getCategory(), dep.getArgNumber())) {
							evalDeps.add(new ResolvedDependency(dep.getHead(), dep.getCategory(), dep.getArgNumber(),
									argument, SRLFrame.NONE, null));

						}

					}
				}
			} else {
				evalDeps = new HashSet<>();
				if (isDev) {
					continue;

				}
			}

			results.add(evaluate(evalDeps, asResolvedDependencies(gold.getDependencies()), precisionErrors, correct,
					perRelationResults));
		}

		int i = 0;
		for (final Entry<String> entry : Multisets.copyHighestCountFirst(precisionErrors).entrySet()) {
			if (correct.count(entry.getElement()) > 10) {
				continue;
			}

			System.err.println(entry.getElement() + "\t" + entry.getCount() + "\t" + correct.count(entry.getElement()));
			if (i++ == 20) {
				break;
			}
		}

		System.err.println();
		for (final java.util.Map.Entry<String, Results> relation : perRelationResults.entrySet().stream()
				.sorted((e1, e2) -> e2.getValue().getFrequency() - e1.getValue().getFrequency())
				.collect(Collectors.toList())) {
			if (relation.getValue().getFrequency() > 100) {
				System.err.println(relation.getKey() + "\t" + Util.twoDP(100.0 * relation.getValue().getF1()));
			}
		}

		System.out.println(results);
		return results;

	}

	private static boolean filter(final String word, final Category category, final int argumentNumber) {
		return filter.contains(category + "." + argumentNumber)
				|| filter.contains(word + ":" + category + "." + argumentNumber);
	}

	private static Set<ResolvedDependency> asResolvedDependencies(final Collection<CCGBankDependency> dependencies) {
		final Set<ResolvedDependency> result = new HashSet<>();
		for (final CCGBankDependency dep : dependencies) {
			result.add(new ResolvedDependency(dep.getSentencePositionOfPredicate(), dep.getCategory(), dep
					.getArgNumber(), dep.getSentencePositionOfArgument(), SRLFrame.NONE, null));
		}

		return result;
	}

	private static Results evaluate(final Set<ResolvedDependency> predicted, final Set<ResolvedDependency> gold,
			final Multiset<String> precisionErrors, final Multiset<String> correct,
			final Map<String, Results> perRelationResults) {

		final Set<ResolvedDependency> correctDeps = Sets.intersection(predicted, gold);

		for (final ResolvedDependency dep : Sets.union(predicted, gold)) {
			final String key = dep.getCategory() + "." + dep.getArgNumber();
			Results results = perRelationResults.get(key);
			if (results == null) {
				results = new Results();
				perRelationResults.put(key, results);
			}
			results.add(new Results(predicted.contains(dep) ? 1 : 0, correctDeps.contains(dep) ? 1 : 0, gold
					.contains(dep) ? 1 : 0));
		}

		addCounts(Sets.difference(predicted, gold), precisionErrors);
		addCounts(correctDeps, correct);

		return new Results(predicted.size(), correctDeps.size(), gold.size());
	}

	private static void addCounts(final Set<ResolvedDependency> deps, final Multiset<String> precisionErrors) {
		for (final ResolvedDependency precisionError : deps) {
			precisionErrors.add(precisionError.getCategory() + "." + precisionError.getArgNumber());
			precisionErrors.add(precisionError.getHead() + ":" + precisionError.getCategory() + "."
					+ precisionError.getArgNumber());
		}
	}

	private static void extractDependencies(final SyntaxTreeNode parse, final Collection<UnlabelledDependency> deps) {
		if (parse.getChildren().size() == 2) {

			deps.addAll(parse.getResolvedUnlabelledDependencies());
		}
		for (final SyntaxTreeNode child : parse.getChildren()) {
			extractDependencies(child, deps);
		}
	}
}
