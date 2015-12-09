package edu.uw.easysrl.syntax.model.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.carrotsearch.hppc.ObjectDoubleHashMap;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.lemmatizer.MorphaStemmer;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.model.feature.LexicalFeature.POSfeature;
import edu.uw.easysrl.util.Util;

public abstract class BilexicalFeature extends Feature {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private final FeatureKey defaultKey;
	private int defaultIndex = 0;
	private double defaultScore = Double.MIN_VALUE;

	BilexicalFeature() {
		super();
		this.defaultKey = new FeatureKey(super.id);
	}

	@Override
	public FeatureKey getDefault() {
		return defaultKey;
	}

	@Override
	public void resetDefaultIndex() {
		defaultIndex = 0;
	}

	public int getFeatureIndex(final List<InputWord> words,
			final SRLLabel role, final int predicateIndex,
			final int argumentIndex,
			final Map<FeatureKey, Integer> featureToIndex) {
		final FeatureKey featureKey = getFeatureKey(words, role,
				predicateIndex, argumentIndex);
		final Integer result = featureToIndex.get(featureKey);
		if (result == null) {
			if (defaultIndex == 0) {
				defaultIndex = featureToIndex.get(defaultKey);
			}

			return defaultIndex;
		}
		return result;
	}

	double getFeatureScore(final List<InputWord> words, final SRLLabel role,
			final int predicateIndex, final int argumentIndex,
			final ObjectDoubleHashMap<FeatureKey> featureToScore) {
		final FeatureKey featureKey = getFeatureKey(words, role,
				predicateIndex, argumentIndex);
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

	public FeatureKey getFeatureKey(final List<InputWord> words,
			final SRLLabel role, final int predicateIndex,
			final int argumentIndex) {

		return getFeatureKey2(words, role, predicateIndex, argumentIndex);
	}

	abstract FeatureKey getFeatureKey2(List<InputWord> words, SRLLabel role,
			int predicateIndex, int argumentIndex);

	boolean isDependentOnPredicateIndex(
			@SuppressWarnings("unused") final boolean isCore) {
		return true;
	}

	boolean isDependentOnArgumentIndex(
			@SuppressWarnings("unused") final boolean isCore) {
		return true;
	}

	private final static String getWord(final List<InputWord> sentence,
			final int position) {
		if (position < 0 || position >= sentence.size()) {
			return "OOR";
		} else {
			return sentence.get(position).word;
		}
	}

	/**
	 * Makes a collection of bilexical features using the specified set of
	 * clusters and context window
	 */
	public static Collection<BilexicalFeature> getBilexicalFeatures(
			final List<Clustering> clusterings, final int window) {
		Collection<BilexicalFeature> dependencyFeaturesWithClustering;
		dependencyFeaturesWithClustering = new ArrayList<>();

		for (final Clustering clustering : clusterings) {

			dependencyFeaturesWithClustering.add(new LemmaAndRoleAndArgCluster(
					new LexicalFeature(clustering, 0)));
		}

		dependencyFeaturesWithClustering.add(new LemmaAndRoleAndArgCluster(
				new POSfeature(0)));

		for (int i = -window; i <= window; i++) {
			for (final Clustering clustering : clusterings) {
				dependencyFeaturesWithClustering
						.add(new ContextDependencyFeature(true,
								new LexicalFeature(clustering, i)));
				dependencyFeaturesWithClustering
						.add(new ContextDependencyFeature(false,
								new LexicalFeature(clustering, i)));
			}

			dependencyFeaturesWithClustering.add(new ContextDependencyFeature(
					true, new POSfeature(i)));
			dependencyFeaturesWithClustering.add(new ContextDependencyFeature(
					false, new POSfeature(i)));
		}

		dependencyFeaturesWithClustering.add(new DistanceDependencyFeature(10,
				null));

		dependencyFeaturesWithClustering
				.add(new LemmaAndRoleAndArgCapitalization());

		return dependencyFeaturesWithClustering;
	}

	// eat.ARG1-->cluster47
	private static class LemmaAndRoleAndArgCluster extends BilexicalFeature {
		/**
		 *
		 */
		private static final long serialVersionUID = 7347370597022610709L;
		private final LexicalFeature argumentFeature;

		private LemmaAndRoleAndArgCluster(final LexicalFeature argumentFeature) {
			super();
			this.argumentFeature = argumentFeature;
		}

		@Override
		FeatureKey getFeatureKey2(final List<InputWord> words,
				final SRLLabel role, final int predicateIndex,
				final int argumentIndex) {
			return hash(super.id,
					MorphaStemmer.stemToken(words.get(predicateIndex).word)
							.hashCode(), role.hashCode(), argumentFeature
							.getValue(words, argumentIndex).hashCode());
		}
	}

	/**
	 * Context features of the word which isn't the Propbank predicate. e.g. if
	 * the previous word is 'to', then is might be a DEST argument if the next
	 * word is 'London' it might be a LOC adjunct
	 */
	private static class ContextDependencyFeature extends BilexicalFeature {
		/**
		 *
		 */
		private static final long serialVersionUID = -2198134192575922596L;
		private final LexicalFeature feature;
		private final boolean predicateContext;

		private ContextDependencyFeature(final boolean predicateContext,
				final LexicalFeature feature) {
			super();
			this.feature = feature;
			this.predicateContext = predicateContext;
		}

		@Override
		FeatureKey getFeatureKey2(final List<InputWord> words,
				final SRLLabel role, final int predicateIndex,
				final int argumentIndex) {

			int nonVerbIndex;

			if (role.isCoreArgument() == predicateContext) {
				nonVerbIndex = argumentIndex;
			} else {
				nonVerbIndex = predicateIndex;
			}

			final Object value = feature.getValue(words, nonVerbIndex);
			final FeatureKey key = hash(super.id, role.hashCode(),
					feature.getOffset(), value.hashCode());

			return key;
		}

		@Override
		boolean isDependentOnPredicateIndex(final boolean isCore) {
			return isCore != predicateContext;
		}

		@Override
		boolean isDependentOnArgumentIndex(final boolean isCore) {
			return isCore == predicateContext;
		}
	}

	private static class DistanceDependencyFeature extends BilexicalFeature {

		private static final long serialVersionUID = -2198134192575922596L;
		private final int maxValue;
		private final Integer includeWordAtOffset;

		private DistanceDependencyFeature(final int maxValue,
				final Integer includeWordAtOffset) {
			super();
			this.maxValue = maxValue;
			this.includeWordAtOffset = includeWordAtOffset;
		}

		@Override
		FeatureKey getFeatureKey2(final List<InputWord> words,
				final SRLLabel role, final int predicateIndex,
				final int argumentIndex) {
			final int distance = predicateIndex - argumentIndex;
			int normalizedDistance;
			if (distance < -maxValue) {
				normalizedDistance = -maxValue;
			} else if (distance > maxValue) {
				normalizedDistance = maxValue;
			} else {
				normalizedDistance = distance;
			}

			if (includeWordAtOffset == null) {
				return hash(super.id, role.hashCode(), normalizedDistance);
			} else {
				return hash(super.id, role.hashCode(), normalizedDistance,
						getWord(words, includeWordAtOffset).hashCode());
			}

		}

	}

	// eat.ARG1-->cluster47
	private static class LemmaAndRoleAndArgCapitalization extends
			BilexicalFeature {
		/**
		 *
		 */
		private static final long serialVersionUID = 2787707266221162597L;

		public LemmaAndRoleAndArgCapitalization() {
			super();
		}

		@Override
		FeatureKey getFeatureKey2(final List<InputWord> words,
				final SRLLabel role, final int predicateIndex,
				final int argumentIndex) {
			return hash(super.id,
					MorphaStemmer.stemToken(words.get(predicateIndex).word)
							.hashCode(), role.hashCode(),
					Util.isCapitalized(words.get(argumentIndex).word) ? 7 : 13);
		}
	}
}