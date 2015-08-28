package edu.uw.easysrl.syntax.training;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.training.FeatureForest.ConjunctiveNode;
import edu.uw.easysrl.syntax.training.FeatureForest.DisjunctiveNode;
import edu.uw.easysrl.util.Util.Scored;

/**
 * Implements Inside Outside algorithm, used to compute marginal probabilities of features.
 *
 */
class InsideOutside {

	private final FeatureSet featureSet;
	private final double[] featureWeights;

	private final FeatureForest parses;
	private final List<InputWord> words;
	private final double logNormalizingConstant;

	public double getLogNormalizingConstant() {
		return logNormalizingConstant;
	}

	private final Map<ConjunctiveNode, Double> insideScoreConjunctiveCache = new HashMap<>();
	private final Map<DisjunctiveNode, Double> insideScoreDisjunctiveCache = new HashMap<>();
	private final Map<DisjunctiveNode, Double> outsideScoreDisjunctiveCache = new HashMap<>();

	private final Map<FeatureKey, Integer> featureToIndexMap;

	void updateFeatureExpectations(final double[] result) {

		for (final ConjunctiveNode node : parses.getConjunctiveNodes()) {
			final double nodeScore = marginalProbability(node);
			node.updateExpectations(words, nodeScore, result, featureSet, featureToIndexMap);
		}

	}

	double updateFeatureExpectationsViterbi(final double[] result) {
		final Scored<Collection<ConjunctiveNode>> viterbiParse = getViterbiParse();
		for (final ConjunctiveNode node : viterbiParse.getObject()) {
			final double nodeScore = 1.0;
			node.updateExpectations(words, nodeScore, result, featureSet, featureToIndexMap);
		}

		return viterbiParse.getScore();
	}

	double marginalProbability(final ConjunctiveNode node) {
		double nodeScore = logInsideScoreConjunctive(node) + logOutsideScoreConjunctive(node) - logNormalizingConstant;

		nodeScore = Math.exp(nodeScore);
		return nodeScore;
	}

	static double logSum(final Double a, final Double b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}

		final double max = Math.max(a, b);
		final double min = Math.min(a, b);
		final double result = max + Math.log(1 + Math.exp(min - max));

		return result;
	}

	InsideOutside(final FeatureForest featureForest, final FeatureSet featureSet, final List<InputWord> words,
			final Map<FeatureKey, Integer> featureToIndexMap,

			final double[] featureWeights) {
		this.words = words;
		this.featureSet = featureSet;

		this.parses = featureForest;
		this.featureWeights = featureWeights;
		this.featureToIndexMap = featureToIndexMap;

		Double logZ = null;

		for (final DisjunctiveNode root : parses.getRoots()) {
			final double rootScore = logInsideScoreDisjunctive(root);
			logZ = logSum(rootScore, logZ);
		}

		this.logNormalizingConstant = logZ;
	}

	private final double logInsideScoreDisjunctive(final DisjunctiveNode disjunctiveNode) {

		Double result = insideScoreDisjunctiveCache.get(disjunctiveNode);
		if (result == null) {
			if (disjunctiveNode.getChildren().size() == 0) {
				throw new RuntimeException();
			}

			for (final ConjunctiveNode conjunctiveNode : disjunctiveNode.getChildren()) {
				result = logSum(result, logInsideScoreConjunctive(conjunctiveNode));
			}

			if (result == null) {
				result = Double.NEGATIVE_INFINITY;
			}
			insideScoreDisjunctiveCache.put(disjunctiveNode, result);
		}

		return result;
	}

	private final double logInsideScoreConjunctive(final ConjunctiveNode conjunctiveNode) {
		Double result = insideScoreConjunctiveCache.get(conjunctiveNode);
		if (result == null) {

			final Collection<DisjunctiveNode> children = conjunctiveNode.getChildren();
			final double logScoreOfFeaturesAtNode = conjunctiveNode.getLogScore(words, featureSet, featureToIndexMap,
					featureWeights);

			result = logScoreOfFeaturesAtNode;
			for (final DisjunctiveNode daughter : children) {
				result = result + logInsideScoreDisjunctive(daughter);
			}

			insideScoreConjunctiveCache.put(conjunctiveNode, result);
		}

		return result;
	}

	private final double logOutsideScoreDisjunctive(final DisjunctiveNode disjunctiveNode) {
		Double result = outsideScoreDisjunctiveCache.get(disjunctiveNode);
		if (result == null) {

			if (disjunctiveNode.getParents().size() == 0) {
				result = 0.0;
			} else {
				for (final ConjunctiveNode parent : disjunctiveNode.getParents()) {
					final double logOutsideScoreForParent = logOutsideScoreConjunctive(parent);
					final double logScoreOfFeaturesAtParent = parent.getLogScore(words, featureSet, featureToIndexMap,
							featureWeights);

					double resultForParent = logOutsideScoreForParent + logScoreOfFeaturesAtParent;

					for (final DisjunctiveNode sister : parent.getChildren()) {
						if (sister == disjunctiveNode) {
							continue;
						}
						resultForParent = resultForParent + logInsideScoreDisjunctive(sister);
					}

					result = logSum(result, resultForParent);
				}

			}

			outsideScoreDisjunctiveCache.put(disjunctiveNode, result);
		}

		return result;
	}

	private final double logOutsideScoreConjunctive(final ConjunctiveNode conjunctiveNode) {
		return logOutsideScoreDisjunctive(conjunctiveNode.getParent());
	}

	public double getViterbiScore() {
		double result = Double.NEGATIVE_INFINITY;
		final Map<DisjunctiveNode, Double> disjunctiveCache = new HashMap<>();
		final Map<ConjunctiveNode, Double> conjunctiveCache = new HashMap<>();

		for (final DisjunctiveNode d : parses.getRoots()) {
			result = Math.max(result, logViterbiInsideScoreDisjunctive(d, disjunctiveCache, conjunctiveCache));
		}
		return result;
	}

	public Scored<Collection<ConjunctiveNode>> getViterbiParse() {
		final Map<DisjunctiveNode, Scored<Collection<ConjunctiveNode>>> disjunctiveCache = new HashMap<>();
		final Map<ConjunctiveNode, Scored<Collection<ConjunctiveNode>>> conjunctiveCache = new HashMap<>();

		Scored<Collection<ConjunctiveNode>> result = new Scored<>(Collections.emptyList(), Double.NEGATIVE_INFINITY);

		for (final DisjunctiveNode d : parses.getRoots()) {

			final Scored<Collection<ConjunctiveNode>> child = getViterbiParseDisjuctive(d, disjunctiveCache,
					conjunctiveCache);

			if (child.getScore() > result.getScore()) {
				result = child;
			}
		}
		return result;
	}

	private final double logViterbiInsideScoreDisjunctive(final DisjunctiveNode disjunctiveNode,
			final Map<DisjunctiveNode, Double> disjunctiveCache, final Map<ConjunctiveNode, Double> conjunctiveCache) {

		Double result = disjunctiveCache.get(disjunctiveNode);
		if (result == null) {
			if (disjunctiveNode.getChildren().size() == 0) {
				throw new RuntimeException();
			}

			result = Double.NEGATIVE_INFINITY;

			for (final ConjunctiveNode conjunctiveNode : disjunctiveNode.getChildren()) {
				result = Math.max(result,
						logViterbiInsideScoreConjunctive(conjunctiveNode, disjunctiveCache, conjunctiveCache));
			}

			if (result == null) {
				result = Double.NEGATIVE_INFINITY;
			}
			disjunctiveCache.put(disjunctiveNode, result);
		}

		return result;
	}

	private final double logViterbiInsideScoreConjunctive(final ConjunctiveNode conjunctiveNode,
			final Map<DisjunctiveNode, Double> disjunctiveCache, final Map<ConjunctiveNode, Double> conjunctiveCache) {
		Double result = conjunctiveCache.get(conjunctiveNode);
		if (result == null) {

			final Collection<DisjunctiveNode> children = conjunctiveNode.getChildren();
			final double logScoreOfFeaturesAtNode = conjunctiveNode.getLogScore(words, featureSet, featureToIndexMap,
					featureWeights);

			result = logScoreOfFeaturesAtNode;
			for (final DisjunctiveNode daughter : children) {
				result = result + logViterbiInsideScoreDisjunctive(daughter, disjunctiveCache, conjunctiveCache);
			}

			conjunctiveCache.put(conjunctiveNode, result);
		}

		return result;
	}

	private final Scored<Collection<ConjunctiveNode>> getViterbiParseConjuctive(final ConjunctiveNode conjunctiveNode,
			final Map<DisjunctiveNode, Scored<Collection<ConjunctiveNode>>> disjunctiveCache,
			final Map<ConjunctiveNode, Scored<Collection<ConjunctiveNode>>> conjunctiveCache) {
		Scored<Collection<ConjunctiveNode>> result = conjunctiveCache.get(conjunctiveNode);

		if (result == null) {
			final Collection<ConjunctiveNode> nodes = new ArrayList<>();

			final Collection<DisjunctiveNode> children = conjunctiveNode.getChildren();
			final double logScoreOfFeaturesAtNode = conjunctiveNode.getLogScore(words, featureSet, featureToIndexMap,
					featureWeights);

			double score = logScoreOfFeaturesAtNode;
			nodes.add(conjunctiveNode);

			for (final DisjunctiveNode daughter : children) {
				final Scored<Collection<ConjunctiveNode>> child = getViterbiParseDisjuctive(daughter, disjunctiveCache,
						conjunctiveCache);
				score += child.getScore();
				nodes.addAll(child.getObject());
			}

			result = new Scored<>(nodes, score);
			conjunctiveCache.put(conjunctiveNode, result);
		}

		return result;
	}

	private Scored<Collection<ConjunctiveNode>> getViterbiParseDisjuctive(final DisjunctiveNode disjunctiveNode,
			final Map<DisjunctiveNode, Scored<Collection<ConjunctiveNode>>> disjunctiveCache,
			final Map<ConjunctiveNode, Scored<Collection<ConjunctiveNode>>> conjunctiveCache) {

		Scored<Collection<ConjunctiveNode>> result = disjunctiveCache.get(disjunctiveNode);
		if (result == null) {

			result = new Scored<>(Collections.emptyList(), Double.NEGATIVE_INFINITY);

			for (final ConjunctiveNode conjunctiveNode : disjunctiveNode.getChildren()) {

				final Scored<Collection<ConjunctiveNode>> child = getViterbiParseConjuctive(conjunctiveNode,
						disjunctiveCache, conjunctiveCache);

				if (child.getScore() > result.getScore()) {
					result = child;
				}
			}

			disjunctiveCache.put(disjunctiveNode, result);
		}

		return result;
	}
}
