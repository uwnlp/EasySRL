package edu.uw.easysrl.syntax.model.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.carrotsearch.hppc.ObjectDoubleHashMap;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.lemmatizer.MorphaStemmer;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

public abstract class ArgumentSlotFeature extends Feature {

	/**
	 *
	 */
	private static final long serialVersionUID = -7424348475245869871L;

	private final FeatureKey defaultKey;
	private int defaultIndex = 0;
	private double defaultScore = Double.MIN_VALUE;

	/**
	 * fall:to --> ARG3
	 */
	private static ArgumentSlotFeature lemmaAndPrepositionAndRoleFeature = new ArgumentSlotFeature() {

		private static final long serialVersionUID = -5087443819141589517L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, role.hashCode(), preposition.hashCode(),
					MorphaStemmer.stemToken(words.get(predicateIndex).word).hashCode());

		}

	};

	/**
	 * NP:ARG0 PR:NONE
	 */
	private static ArgumentSlotFeature argumentCategoryAndRole = new ArgumentSlotUnlexicalizedFeature() {

		private static final long serialVersionUID = -5087443819141589517L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, role.hashCode(), category.getArgument(argumentNumber).hashCode());
		}

	};

	/**
	 * S[pss]\NP_0 = ARG1
	 */
	public static ArgumentSlotFeature argumentSlotDependencyFeature = new ArgumentSlotUnlexicalizedFeature() {

		private static final long serialVersionUID = -5087443819141589517L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, role.hashCode(), category.hashCode(), argumentNumber);

		}

	};

	/**
	 * Hyphenated predicates don't seem to get PropBank entries, e.g. re-entered.
	 */
	private static ArgumentSlotFeature hyphenAndRoleFeature = new ArgumentSlotFeature() {

		private static final long serialVersionUID = 4705512175495241740L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, role.isCoreArgument() ? 13 : 7, words.get(predicateIndex).word.indexOf("-") > -1 ? 13
					: 7);
		}

	};

	private static ArgumentSlotFeature keyArgumentSlot = new ArgumentSlotFeature() {

		private static final long serialVersionUID = -5087443819141589517L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, MorphaStemmer.stemToken(words.get(predicateIndex).word).hashCode(),
					makeKey(preposition, argumentNumber, category).hashCode(), role.hashCode());
		}

	};

	/**
	 * want:(S[to\NP)):ARG1 get:PR:NONE
	 */
	private static ArgumentSlotFeature lemmaArgumentCategoryAndRole = new ArgumentSlotFeature() {

		private static final long serialVersionUID = -5087443819141589517L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, MorphaStemmer.stemToken(words.get(predicateIndex).word).hashCode(), role.hashCode(),
					category.getArgument(argumentNumber).hashCode());
		}

	};

	/**
	 * open:S[dcl]\NP_0:ARG1 be:(S[dcl]\NP)/NP_0:NONE
	 */
	private static ArgumentSlotFeature lemmaArgumentSlot = new ArgumentSlotFeature() {

		private static final long serialVersionUID = -5087443819141589517L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, MorphaStemmer.stemToken(words.get(predicateIndex).word).hashCode(), role.hashCode(),
					category.hashCode(), argumentNumber);
		}

	};

	ArgumentSlotFeature() {
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

	double getFeatureScore(final List<InputWord> words, final int wordIndex, final SRLLabel role,
			final Category category, final int argumentNumber, final Preposition preposition,
			final ObjectDoubleHashMap<FeatureKey> featureToScore) {
		final FeatureKey featureKey = getFeatureKey(words, wordIndex, role, category, argumentNumber, preposition);
		final double result = featureToScore.getOrDefault(featureKey, Double.MIN_VALUE);
		if (result == Double.MIN_VALUE) {
			if (defaultScore == Double.MIN_VALUE) {
				defaultScore = featureToScore.getOrDefault(defaultKey, Double.MIN_VALUE);
			}

			return defaultScore;
		}
		return result;
	}

	public Integer getFeatureIndex(final List<InputWord> words, final int wordIndex, final SRLLabel role,
			final Category category, final int argumentNumber, final Preposition preposition,
			final Map<FeatureKey, Integer> featureToIndex) {
		final FeatureKey featureKey = getFeatureKey(words, wordIndex, role, category, argumentNumber, preposition);
		final Integer result = featureToIndex.get(featureKey);
		if (result == null) {
			if (defaultIndex == 0) {
				defaultIndex = featureToIndex.get(defaultKey);
			}
			return defaultIndex;
		}
		return result;
	}

	public abstract FeatureKey getFeatureKey(List<InputWord> words, int wordIndex, SRLLabel role, Category category,
			int argumentNumber, Preposition preposition);

	public boolean isLexicalized() {
		return true;
	}

	public static String makeKey(final Preposition preposition, final int argumentNumber, final Category category) {
		String result;

		final boolean passive = category.isFunctionInto(Category.valueOf("S[pss]"));
		final boolean adjectival = category.isFunctionInto(Category.valueOf("S[adj]"));

		if (preposition != Preposition.NONE) {
			result = preposition.toString();
		} else if (category.getArgument(argumentNumber) == Category.NP) {
			// result = "NP" + dep.getValue().getArgNumber();
			int j = 0;
			for (int i = 0; i <= argumentNumber; i++) {
				if (category.getArgument(i) == Category.NP) {
					j++;
				}
			}

			int k = 0;
			for (int i = 0; i <= category.getNumberOfArguments(); i++) {
				if (category.getArgument(i) == Category.NP) {
					k++;
				}
			}

			result = "NP" + j + "_" + k;

			if (passive) {
				result = result + "_pss";
			}

			if (adjectival) {
				result = result + "_adj";
			}
		} else {
			result = category.getArgument(argumentNumber).toString();
		}

		return result;
	}

	private static abstract class ArgumentSlotUnlexicalizedFeature extends ArgumentSlotFeature {
		/**
		 *
		 */
		private static final long serialVersionUID = -6934444486136907730L;

		private ArgumentSlotUnlexicalizedFeature() {
			super();
		}

		@Override
		public Integer getFeatureIndex(final List<InputWord> words, final int wordIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition,
				final Map<FeatureKey, Integer> featureToIndex) {
			return super.getFeatureIndex(words, wordIndex, role, category, argumentNumber, preposition, featureToIndex);
		}

		@Override
		public boolean isLexicalized() {
			return false;
		}
	}

	/**
	 * S[pss]\NP_0 = ARG1
	 */
	private static ArgumentSlotFeature labelBiasFeature = new ArgumentSlotUnlexicalizedFeature() {

		private static final long serialVersionUID = -5087443819141589517L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, role.hashCode());

		}

	};

	/**
	 * CCG-arg0 = SRL-ARG0
	 */
	private static ArgumentSlotFeature argumentNumberDependencyFeature = new ArgumentSlotUnlexicalizedFeature() {

		private static final long serialVersionUID = 2147289965807795425L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, role.hashCode(), argumentNumber);
		}

	};

	/**
	 * 1_of_2 = SRL-ARG0
	 */
	private static ArgumentSlotFeature argumentPositionRoleFeature = new ArgumentSlotUnlexicalizedFeature() {

		private static final long serialVersionUID = 2147289965807795425L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, role.hashCode(), argumentNumber, category.getNumberOfArguments());
		}

	};

	private static ArgumentSlotFeature lemmaAndRoleFeature = new ArgumentSlotFeature() {

		/**
		 *
		 */
		private static final long serialVersionUID = -1167453701712568542L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int wordIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, MorphaStemmer.stemToken(words.get(wordIndex).word).hashCode(), role.hashCode());

		}

	};

	/**
	 * open.1_2 = SRL-ARG0
	 */
	private static ArgumentSlotFeature argumentPositionRoleAndLemmaFeature = new ArgumentSlotFeature() {

		private static final long serialVersionUID = 2147289965807795425L;

		@Override
		public FeatureKey getFeatureKey(final List<InputWord> words, final int predicateIndex, final SRLLabel role,
				final Category category, final int argumentNumber, final Preposition preposition) {
			return hash(super.id, role.hashCode(), argumentNumber,
					MorphaStemmer.stemToken(words.get(predicateIndex).word).hashCode(), category.getNumberOfArguments());
		}

	};

	public final static Collection<ArgumentSlotFeature> argumentSlotFeatures;
	static {
		argumentSlotFeatures = new ArrayList<>();
		argumentSlotFeatures.add(argumentNumberDependencyFeature);
		argumentSlotFeatures.add(argumentSlotDependencyFeature);

		argumentSlotFeatures.add(keyArgumentSlot);
		argumentSlotFeatures.add(lemmaArgumentSlot);
		argumentSlotFeatures.add(lemmaAndRoleFeature);
		argumentSlotFeatures.add(lemmaArgumentCategoryAndRole);
		argumentSlotFeatures.add(argumentPositionRoleAndLemmaFeature);
		argumentSlotFeatures.add(hyphenAndRoleFeature);
		// argumentSlotFeatures.add(isCopulaFeature);

		argumentSlotFeatures.add(argumentCategoryAndRole);
		argumentSlotFeatures.add(labelBiasFeature);
		argumentSlotFeatures.add(argumentPositionRoleFeature);
		// argumentSlotFeatures.add(slotAndPrepositionAndRoleFeature);
		argumentSlotFeatures.add(lemmaAndPrepositionAndRoleFeature);

	}

}