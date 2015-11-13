package edu.uw.easysrl.syntax.model.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.carrotsearch.hppc.ObjectDoubleHashMap;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;

/**
 *
 * Caches out features to save re-computing them
 */
public class FeatureCache {

	private final ObjectDoubleHashMap<SRLLabel>[] predicateToLabelToScore;
	private final ObjectDoubleHashMap<SRLLabel>[] argumentToLabelToScore;
	private final SlotFeatureCache slotFeatureCache;
	private final List<InputWord> words;

	private final List<Map<Category, Double>> wordToCategoryToScore;

	private final List<BilexicalFeature> justPredicateFeaturesCore = new ArrayList<>();
	private final List<BilexicalFeature> justArgumentFeaturesCore = new ArrayList<>();;
	private final List<BilexicalFeature> justPredicateFeaturesAdjunct = new ArrayList<>();
	private final List<BilexicalFeature> justArgumentFeaturesAdjunct = new ArrayList<>();;
	private final List<BilexicalFeature> bilexicalFeatures = new ArrayList<>();
	private final ObjectDoubleHashMap<FeatureKey> featureToScore;

	@SuppressWarnings("unchecked")
	public FeatureCache(final List<InputWord> words, final ObjectDoubleHashMap<FeatureKey> featureToScore,
			final FeatureSet featureSet, final double supertaggerWeight, final SlotFeatureCache slotFeatureCache) {

		this.slotFeatureCache = slotFeatureCache;
		this.wordToCategoryToScore = featureSet.lexicalCategoryFeatures.getCategoryScores(words, supertaggerWeight);

		this.predicateToLabelToScore = new ObjectDoubleHashMap[words.size()];
		this.argumentToLabelToScore = new ObjectDoubleHashMap[words.size()];
		this.words = words;
		this.featureToScore = featureToScore;

		// TODO uuuurgh tidy all this if it works!
		for (final BilexicalFeature feature : featureSet.dependencyFeatures) {

			if (feature.isDependentOnPredicateIndex(true)) {
				if (feature.isDependentOnArgumentIndex(true)) {
					// both
					bilexicalFeatures.add(feature);
				} else {
					// just predicate
					justPredicateFeaturesCore.add(feature);
				}
			} else {
				// just argument
				justArgumentFeaturesCore.add(feature);

			}
		}

		for (final BilexicalFeature feature : featureSet.dependencyFeatures) {

			if (feature.isDependentOnPredicateIndex(false)) {
				if (feature.isDependentOnArgumentIndex(false)) {
					// both
				} else {
					// just predicate
					justPredicateFeaturesAdjunct.add(feature);
				}
			} else {
				// just argument
				justArgumentFeaturesAdjunct.add(feature);

			}
		}
	}

	public double getScore(final int predicateIndex, final SRLLabel role, final int argumentIndex) {
		final ObjectDoubleHashMap<SRLLabel> predicateLabelToScore = getLabelToScore(predicateIndex,
				predicateToLabelToScore);
		final ObjectDoubleHashMap<SRLLabel> argumentLabelToScore = getLabelToScore(argumentIndex,
				argumentToLabelToScore);
		double result = 0.0;

		// Features that only apply to the local context of the predicate. These will be shared across all the word's
		// dependencies.
		final double justPredicateScore = predicateLabelToScore.getOrDefault(role, Double.MIN_VALUE);
		if (justPredicateScore == Double.MIN_VALUE) {
			double score = 0.0;
			for (final BilexicalFeature feature : role.isCoreArgument() ? justPredicateFeaturesCore
					: justPredicateFeaturesAdjunct) {
				score += feature.getFeatureScore(words, role, predicateIndex, argumentIndex, featureToScore);
			}

			predicateLabelToScore.put(role, score);
			result += score;
		} else {
			result += justPredicateScore;
		}

		// Features that only apply to the local context of the argument. These will be shared across all the word's
		// dependencies.
		final double justArgumentScore = argumentLabelToScore.getOrDefault(role, Double.MIN_VALUE);
		if (justArgumentScore == Double.MIN_VALUE) {
			double score = 0.0;
			for (final BilexicalFeature feature : role.isCoreArgument() ? justArgumentFeaturesCore
					: justArgumentFeaturesAdjunct) {
				score += feature.getFeatureScore(words, role, predicateIndex, argumentIndex, featureToScore);
			}

			argumentLabelToScore.put(role, score);
			result += score;
		} else {
			result += justArgumentScore;
		}

		// Bilexical features.
		for (final BilexicalFeature feature : bilexicalFeatures) {
			result += feature.getFeatureScore(words, role, predicateIndex, argumentIndex, featureToScore);
		}

		return result;
	}

	private ObjectDoubleHashMap<SRLLabel> getLabelToScore(final int predicateIndex,
			final ObjectDoubleHashMap<SRLLabel>[] predicateToLabelToScore) {
		ObjectDoubleHashMap<SRLLabel> labelToScore = predicateToLabelToScore[predicateIndex];
		if (labelToScore == null) {
			labelToScore = new ObjectDoubleHashMap<>();
			predicateToLabelToScore[predicateIndex] = labelToScore;
		}
		return labelToScore;
	}

	public double getScore(final List<InputWord> words, final int wordIndex, final Category category,
			final Preposition preposition, final int slot, final SRLLabel role) {
		return slotFeatureCache.getScore(words, wordIndex, category, preposition, slot, role);
	}

	public double getScore(final int wordIndex, final Category category) {
		return wordToCategoryToScore.get(wordIndex).getOrDefault(category, Double.NEGATIVE_INFINITY);
	}

	public Collection<Category> getCategoriesAtIndex(final int wordIndex) {
		return wordToCategoryToScore.get(wordIndex).keySet();
	}

	public static class SlotFeatureCache {
		private final ObjectDoubleHashMap<FeatureKey> featureToScore;
		private final List<ArgumentSlotFeature> lexicalizedSlotFeatures = new ArrayList<>();
		private final List<ArgumentSlotFeature> unlexicalizedSlotFeatures = new ArrayList<>();
		private final double[][][][] categoryToSlotToPrepositionToScore = new double[Category.numberOfCategories()][6][Preposition
				.numberOfPrepositions() + 1][SRLLabel.numberOfLabels()];

		public SlotFeatureCache(final FeatureSet featureSet, final ObjectDoubleHashMap<FeatureKey> featureToScore) {
			for (final ArgumentSlotFeature feature : featureSet.argumentSlotFeatures) {
				if (feature.isLexicalized()) {
					lexicalizedSlotFeatures.add(feature);
				} else {
					unlexicalizedSlotFeatures.add(feature);
				}
			}

			this.featureToScore = featureToScore;
		}

		public double getScore(final List<InputWord> words, final int wordIndex, final Category category,
				final Preposition preposition, final int slot, final SRLLabel role) {
			final int prepIndex = preposition.getID();
			double score = categoryToSlotToPrepositionToScore[category.getID()][slot][prepIndex][role.getID()];
			if (score == 0.0) {
				for (final ArgumentSlotFeature feature : unlexicalizedSlotFeatures) {
					score += feature.getFeatureScore(words, wordIndex, role, category, slot, preposition,
							featureToScore);
				}

				categoryToSlotToPrepositionToScore[category.getID()][slot][prepIndex][role.getID()] = score;
			}

			for (final ArgumentSlotFeature feature : lexicalizedSlotFeatures) {
				score += feature.getFeatureScore(words, wordIndex, role, category, slot, preposition, featureToScore);
			}

			return score;
		}

	}

}
