package edu.uw.easysrl.syntax.training;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.syntax.training.CompressedChart.Key;
import edu.uw.easysrl.syntax.training.CompressedChart.TreeValueBinary;
import edu.uw.easysrl.syntax.training.CompressedChart.TreeValueUnary;
import edu.uw.easysrl.syntax.training.CompressedChart.Value;

/**
 * Given a complete chart for a sentence, and a set of gold-standard dependencies, this finds a sub-chart of that
 * maximizes agreement with the dependencies
 *
 */
public class GoldChartFinder {

	private final CompressedChart completeChart;
	private final boolean usingCCGbankDepenencies;

	public GoldChartFinder(final CompressedChart completeChart, final boolean usingCCGbankDepenencies) {
		super();
		this.completeChart = completeChart;
		this.usingCCGbankDepenencies = usingCCGbankDepenencies;
	}

	private static class Scored<T> {
		private final T object;
		private final int matchedDependencies;

		public Scored(final T object, final int matchedDependencies) {
			this.object = object;
			this.matchedDependencies = matchedDependencies;
		}

	}

	CompressedChart goldChart(final Sentence sentence, final CutoffsDictionaryInterface cutoffs) {

		final Set<SRLDependency> goldDeps = getTrainingDependencies(sentence);

		return goldChart(goldDeps, cutoffs);
	}

	/**
	 *
	 */
	private Set<SRLDependency> getTrainingDependencies(final Sentence sentence) {
		final Set<SRLDependency> goldDeps = new HashSet<>();
		if (usingCCGbankDepenencies) {
			for (final ResolvedDependency dep : sentence.getCCGBankDependencyParse().getResolvedDependencies()) {
				goldDeps.add(new SRLDependency(sentence.getWords().get(dep.getHead()), dep.getHead(), Collections
						.singletonList(dep.getArgumentIndex()), SRLLabel.fromString("ARG" + (dep.getArgNumber() - 1)),
						dep.getPreposition().toString()));
			}
		} else {
			for (final SRLDependency srl : sentence.getSrlParse().getDependencies()) {
				if (srl.getArgumentPositions().size() > 0) {
					// Ignore PropBank arguments that don't refer to any span in the
					// sentence.
					final List<Integer> firstConstituent = new ArrayList<>();
					for (int i = srl.getFirstArgumentPosition(); i <= srl.getLastArgumentPosition()
							&& srl.getArgumentPositions().contains(i); i++) {

						firstConstituent.add(i);
					}

					final SRLDependency newDep = new SRLDependency(srl.getPredicate(), srl.getPredicateIndex(),
							firstConstituent, srl.getLabel(), srl.getPreposition());

					goldDeps.add(newDep);
				}
			}
		}

		return goldDeps;
	}

	private CompressedChart goldChart(final Set<SRLDependency> goldDeps, final CutoffsDictionaryInterface cutoffs) {
		final Table<Key, Set<SRLDependency>, Scored<Key>> cache = HashBasedTable.create();

		int bestScore = -1;

		Collection<Key> bestKeys = null;

		for (final Key root : completeChart.getRoots()) {
			// Choose the value that maximizes the number of dependencies in
			// GoldDeps

			final Scored<Key> scoredKey = bestKey(root, goldDeps, cache, cutoffs);

			if (scoredKey.matchedDependencies > bestScore) {
				bestKeys = new HashSet<>();
				bestKeys.add(scoredKey.object);
				bestScore = scoredKey.matchedDependencies;
			} else if (scoredKey.matchedDependencies == bestScore) {
				bestKeys.add(scoredKey.object);
			}
		}

		if (bestScore > goldDeps.size()) {
			throw new IllegalStateException();
		}

		if (bestScore == 0) {
			return null;
		}

		return new CompressedChart(completeChart.getWords(), bestKeys);
	}

	private Scored<Key> bestKey(final Key key, final Set<SRLDependency> goldDeps,
			final Table<Key, Set<SRLDependency>, Scored<Key>> cache, final CutoffsDictionaryInterface cutoffs) {
		Scored<Key> result = cache.get(key, goldDeps);
		if (result == null) {

			// Filter the dependencies to only be ones occurring in this part of
			// the chart.
			final Set<SRLDependency> possible = getPossibleDeps(key, goldDeps);

			Set<Value> bestValues = null;
			int bestScore = -1;

			for (final Value value : key.values) {
				// Choose the value that maximizes the number of dependencies in
				// GoldDeps

				final Scored<Value> scoredValue = bestValue(value, possible, cache, cutoffs);

				if (scoredValue.matchedDependencies > bestScore) {
					bestValues = new HashSet<>();
					bestValues.add(scoredValue.object);
					bestScore = scoredValue.matchedDependencies;
				} else if (scoredValue.matchedDependencies == bestScore) {
					bestValues.add(scoredValue.object);
				}
			}
			result = new Scored<>(new Key(key.category, key.startIndex, key.lastIndex, key.ruleClass, bestValues),
					bestScore);

			cache.put(key, goldDeps, result);
		}

		return result;
	}

	/**
	 * Finds the set of dependencies that are potentially matched at this node
	 */
	private Set<SRLDependency> getPossibleDeps(final Key key, final Set<SRLDependency> goldDeps) {
		final Set<SRLDependency> possible = new HashSet<>(goldDeps.size());
		final int startIndex = key.getStartIndex();
		final int endIndex = key.getLastIndex() + 1;
		for (final SRLDependency dep : goldDeps) {
			if (dep.getPredicateIndex() >= startIndex && dep.getPredicateIndex() < endIndex) {
				for (final int arg : dep.getArgumentPositions()) {
					if (arg >= startIndex && arg < endIndex) {
						possible.add(dep);
						break;
					}
				}

			}
		}
		return possible;
	}

	private Scored<Value> bestValue(final Value value, final Set<SRLDependency> goldDeps,
			final Table<Key, Set<SRLDependency>, Scored<Key>> cache, final CutoffsDictionaryInterface cutoffs) {

		final List<Key> children = value.getChildren();
		if (children.size() == 0) {
			return new Scored<>(value, 0);
		}

		final Collection<ResolvedDependency> dependencies = value.getDependencies();
		final Set<SRLDependency> missingDeps;
		int score;
		final Set<ResolvedDependency> labelledDeps;

		if (dependencies.size() > 0) {
			labelledDeps = new HashSet<>(dependencies.size());

			final Set<SRLDependency> matchedDeps = getMatchedDeps(goldDeps, dependencies, cutoffs, labelledDeps);

			score = matchedDeps.size();
			if (matchedDeps.size() > 0) {
				missingDeps = Sets.difference(goldDeps, matchedDeps);
			} else {
				missingDeps = goldDeps;
			}
		} else {
			score = 0;
			missingDeps = goldDeps;
			labelledDeps = Collections.emptySet();
		}

		final List<Key> newChildren = new ArrayList<>(children.size());
		for (final Key child : children) {
			final Scored<Key> key = bestKey(child, missingDeps, cache, cutoffs);
			score += key.matchedDependencies;
			newChildren.add(key.object);
		}

		final Value object;

		if (newChildren.size() == 1) {
			object = new TreeValueUnary(newChildren.get(0), value.getRuleID(), labelledDeps);
		} else if (newChildren.size() == 2) {
			object = new TreeValueBinary(newChildren.get(0), newChildren.get(1), labelledDeps);
		} else {
			throw new IllegalStateException("Shouldn't be here");
		}

		// Avoid potential double-counting
		return new Scored<>(object, Math.min(score, goldDeps.size()));
	}

	/**
	 * Finds the set of SRL dependencies that match dependencies resolved at this node
	 */
	private Set<SRLDependency> getMatchedDeps(final Set<SRLDependency> goldDeps,
			final Collection<ResolvedDependency> dependencies, final CutoffsDictionaryInterface cutoffs,
			final Collection<ResolvedDependency> newDeps) {
		final Set<SRLDependency> matchedDeps = new HashSet<>(dependencies.size());
		for (final ResolvedDependency dep : dependencies) {
			final int predicateIndex = dep.getHead();
			final int argumentIndex = predicateIndex + dep.getOffset();
			boolean isSRL = false;

			for (final SRLDependency srl : goldDeps) {
				if (!matchedDeps.contains(srl)
						&& cutoffs.isFrequent(dep.getCategory(), dep.getArgNumber(), srl.getLabel())
						&& cutoffs.getRoles(completeChart.getWords().get(dep.getHead()).word, dep.getCategory(),
								dep.getPreposition(), dep.getArgNumber()).contains(srl.getLabel())
						&& matches(predicateIndex, argumentIndex, srl, dep.getPreposition())) {

					matchedDeps.add(srl);
					newDeps.add(dep.overwriteLabel(srl.getLabel()));
					isSRL = true;
					break;
				}
			}

			if (!isSRL) {
				// Dependencies that aren't in the gold training data.
				// For SRL, these should get 'NONE', for CCG they should get ARG_n
				if (usingCCGbankDepenencies) {
					newDeps.add(dep.overwriteLabel(SRLLabel.fromString("ARG" + (dep.getArgNumber() - 1))));
				} else {
					newDeps.add(dep.overwriteLabel(SRLFrame.NONE));
				}
			}

		}
		return matchedDeps;
	}

	private boolean matches(final int predicateIndex, final int argumentIndex, final SRLDependency srl,
			final Preposition preposition) {
		return ((srl.isCoreArgument() && srl.getPredicateIndex() == predicateIndex && srl.getArgumentPositions()
				.contains(argumentIndex)) || (!srl.isCoreArgument() && srl.getPredicateIndex() == argumentIndex && srl
				.getArgumentPositions().contains(predicateIndex)))
				&& Preposition.fromString(srl.getPreposition()) == preposition;
	}
}