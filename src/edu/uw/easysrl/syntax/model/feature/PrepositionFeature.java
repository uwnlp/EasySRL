package edu.uw.easysrl.syntax.model.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.carrotsearch.hppc.ObjectDoubleHashMap;

import edu.uw.easysrl.lemmatizer.MorphaStemmer;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

public abstract class PrepositionFeature extends Feature {

	/**
	 *
	 */
	private static final long serialVersionUID = -7424348475245869871L;

	private final FeatureKey defaultKey;
	private int defaultIndex = 0;
	private double defaultScore = Double.MIN_VALUE;

	private final static PrepositionFeature wordAndPrepositionFeature = new PrepositionFeature() {

		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words,
				final int wordIndex, final Category category,
				final Preposition preposition, final int argumentNumber) {
			return hash(super.id, preposition.hashCode(),
					words.get(wordIndex).word.hashCode());
		}
	};

	private final static PrepositionFeature categoryAndSlotAndPrepositionFeature = new PrepositionFeature() {

		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words,
				final int wordIndex, final Category category,
				final Preposition preposition, final int argumentNumber) {
			return hash(super.id, preposition.hashCode(), argumentNumber,
					category.hashCode());
		}
	};

	private final static PrepositionFeature lemmaAndPrepositionFeature = new PrepositionFeature() {

		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words,
				final int wordIndex, final Category category,
				final Preposition preposition, final int argumentNumber) {
			return hash(super.id, preposition.hashCode(), MorphaStemmer
					.stemToken(words.get(wordIndex).word).hashCode());
		}
	};

	public final static Collection<PrepositionFeature> prepositionFeaures;
	static {
		prepositionFeaures = new ArrayList<>();
		prepositionFeaures.add(lemmaAndPrepositionFeature);
		prepositionFeaures.add(wordAndPrepositionFeature);
		// prepositionFeaures.add(categoryAndPrepositionFeature);
		prepositionFeaures.add(categoryAndSlotAndPrepositionFeature);

	}

	PrepositionFeature() {
		super();
		this.defaultKey = new FeatureKey(super.id);
	}

	@Override
	public FeatureKey getDefault() {
		return defaultKey;
	}

	@Override
	public void resetDefaultIndex() { defaultIndex = 0; }

	public Integer getFeatureIndex(final List<InputWord> words,
			final int wordIndex, final Category category,
			final Preposition preposition, final int argumentNumber,
			final Map<FeatureKey, Integer> featureToIndex) {
		final FeatureKey featureKey = getFeatureKey(words, wordIndex, category,
				preposition, argumentNumber);
		Integer result = featureToIndex.get(featureKey);
		if (result == null) {
			if (defaultIndex == 0) {
				defaultIndex = featureToIndex.get(defaultKey);
			}
			return defaultIndex;
		}
		return result;
	}

	public abstract FeatureKey getFeatureKey(List<InputWord> words,
			int wordIndex, Category category, Preposition preposition,
			int argumentNumber);

	public double getFeatureScore(final List<InputWord> words,
			final int wordIndex, final Preposition preposition,
			final Category category, final int argumentNumber,
			final ObjectDoubleHashMap<FeatureKey> featureToScore) {
		final FeatureKey featureKey = getFeatureKey(words, wordIndex, category,
				preposition, argumentNumber);
		final double result = featureToScore.getOrDefault(featureKey,
				Double.MIN_VALUE);
		if (result == Double.MIN_VALUE) {
			if (defaultScore == Double.MIN_VALUE) {
				defaultScore = featureToScore.get(defaultKey);
			}

			return defaultScore;
		}
		return result;
	}

}