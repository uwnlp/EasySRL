package edu.uw.easysrl.syntax.model.feature;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.carrotsearch.hppc.ObjectDoubleHashMap;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleClass;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;

public abstract class Feature implements Serializable {
	/**
	 * Unique identifier for this feature.
	 */
	final int id;
	private final static AtomicInteger count = new AtomicInteger();

	protected Feature() {
		this.id = count.getAndIncrement();
	}

	public abstract FeatureKey getDefault();

	public abstract void resetDefaultIndex();

	public static class FeatureKey implements Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final int[] values;
		private final int hashCode;

		FeatureKey(final int... values) {
			this.values = values;
			this.hashCode = Arrays.hashCode(values);

		}

		@Override
		public boolean equals(final Object other) {
			return hashCode() == other.hashCode() && Arrays.equals(values, ((FeatureKey) other).values);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public String toString() {
			return Arrays.toString(values);
		}

		public int[] getValues() {
			return values;
		}
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 6283022233440386377L;

	public static abstract class LexicalCategoryFeature extends Feature {

		/**
		 *
		 */
		private static final long serialVersionUID = -5413460311778529355L;

		public abstract double getValue(List<InputWord> sentence, int word, Category category);

		public abstract FeatureKey getFeatureKey(List<InputWord> inputWords, int wordIndex, Category category);

		@Override
		public abstract FeatureKey getDefault();

	}

	static FeatureKey hash(final int... objects) {

		return new FeatureKey(objects);
	}

	public static abstract class UnaryRuleFeature extends Feature {
		private final FeatureKey defaultKey;
		private int defaultIndex = 0;
		private double defaultScore = Double.MIN_VALUE;

		UnaryRuleFeature() {
			super();
			this.defaultKey = new FeatureKey(super.id);
		}

		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		public double getFeatureScore(final int ruleID, final List<InputWord> sentence, final int spanStart,
				final int spanEnd, final ObjectDoubleHashMap<FeatureKey> featureToScore) {
			final FeatureKey featureKey = getFeatureKey(ruleID, sentence, spanStart, spanEnd);
			final double result = featureToScore.getOrDefault(featureKey, Double.MIN_VALUE);
			if (result == Double.MIN_VALUE) {
				if (defaultScore == Double.MIN_VALUE) {
					defaultScore = featureToScore.get(defaultKey);
				}

				return defaultScore;
			}
			return result;
		}

		@Override
		public FeatureKey getDefault() {
			return defaultKey;
		}

		@Override
		public void resetDefaultIndex() {
			defaultIndex = 0;
		}

		public int getFeatureIndex(final int ruleID, final List<InputWord> words, final int startIndex,
				final int spanEnd, final Map<FeatureKey, Integer> featureToIndexMap) {
			final Integer result = featureToIndexMap.get(getFeatureKey(ruleID, words, startIndex, spanEnd));
			if (result == null) {
				if (defaultIndex == 0) {
					defaultIndex = featureToIndexMap.get(defaultKey);
				}

				return defaultIndex;

			}

			return result;
		}

		public abstract FeatureKey getFeatureKey(final int ruleID, final List<InputWord> sentence, final int spanStart,
				final int spanEnd);

	}

	private final static UnaryRuleFeature unaryRuleIDFeature = new UnaryRuleFeature() {

		private static final long serialVersionUID = 1L;

		@Override
		public FeatureKey getFeatureKey(final int ruleID, final List<InputWord> sentence, final int spanStart,
				final int spanEnd) {
			return hash(super.id, ruleID);
		}
	};

	@SuppressWarnings("unused")
	private final static UnaryRuleFeature unaryRuleIDandLengthFeature = new UnaryRuleFeature() {

		private static final long serialVersionUID = 1L;

		@Override
		public FeatureKey getFeatureKey(final int ruleID, final List<InputWord> sentence, final int spanStart,
				final int spanEnd) {
			return hash(super.id, ruleID, Math.min(10, spanEnd - spanStart));
		}
	};

	@SuppressWarnings("unused")
	private final static UnaryRuleFeature unaryRuleIDandPreviousWordFeature = new UnaryRuleFeature() {

		private static final long serialVersionUID = 1L;

		@Override
		public FeatureKey getFeatureKey(final int ruleID, final List<InputWord> sentence, final int spanStart,
				final int spanEnd) {
			return hash(super.id, ruleID, (spanStart == 0 ? "" : sentence.get(spanStart - 1).word).hashCode());
		}
	};
	public final static Collection<UnaryRuleFeature> unaryRules = Arrays.asList(unaryRuleIDFeature);

	public static abstract class RootCategoryFeature extends Feature {
		private static final long serialVersionUID = 1L;
		private final FeatureKey defaultKey;
		private int defaultIndex = 0;
		private double defaultScore = Double.MIN_VALUE;

		RootCategoryFeature() {
			super();
			this.defaultKey = new FeatureKey(super.id);
		}

		public int getFeatureIndex(final List<InputWord> sentence, final Category category,
				final Map<FeatureKey, Integer> featureToIndex) {
			final FeatureKey featureKey = getFeatureKey(category, sentence);
			final Integer result = featureToIndex.get(featureKey);
			if (result == null) {
				if (defaultIndex == 0) {
					defaultIndex = featureToIndex.get(defaultKey);
				}
				return defaultIndex;
			}
			return result;
		}

		public double getFeatureScore(final List<InputWord> words, final Category category,
				final ObjectDoubleHashMap<FeatureKey> featureToScore) {
			final FeatureKey featureKey = getFeatureKey(category, words);
			final double result = featureToScore.getOrDefault(featureKey, Double.MIN_VALUE);
			if (result == Double.MIN_VALUE) {
				if (defaultScore == Double.MIN_VALUE) {
					defaultScore = featureToScore.get(defaultKey);
				}
				return defaultScore;
			}
			return result;
		}

		@Override
		public FeatureKey getDefault() {
			return defaultKey;
		}

		@Override
		public void resetDefaultIndex() {
			defaultIndex = 0;
		}

		public abstract FeatureKey getFeatureKey(Category category, List<InputWord> sentence);

		public static RootCategoryFeature justCategoryFeature = new RootCategoryFeature() {
			private static final long serialVersionUID = 1L;

			@Override
			public FeatureKey getFeatureKey(final Category category, final List<InputWord> sentence) {
				return hash(super.id, category.hashCode());
			}
		};

		public static RootCategoryFeature categoryAndFirstWord = new RootCategoryFeature() {

			private static final long serialVersionUID = 1L;

			@Override
			public FeatureKey getFeatureKey(final Category category, final List<InputWord> sentence) {
				return hash(super.id, category.hashCode(), sentence.get(0).word.hashCode());
			}
		};

		public static RootCategoryFeature categoryAndLastWord = new RootCategoryFeature() {

			private static final long serialVersionUID = 1L;

			@Override
			public FeatureKey getFeatureKey(final Category category, final List<InputWord> sentence) {
				return hash(super.id, category.hashCode(), sentence.get(sentence.size() - 1).word.hashCode());
			}
		};

		public static RootCategoryFeature categoryAndLength = new RootCategoryFeature() {

			private static final long serialVersionUID = 1L;

			@Override
			public FeatureKey getFeatureKey(final Category category, final List<InputWord> sentence) {
				return hash(super.id, category.hashCode(), sentence.size());
			}
		};

		public final static Collection<RootCategoryFeature> features = Arrays.asList(justCategoryFeature,
				categoryAndFirstWord, categoryAndLastWord, categoryAndLength);

	}

	public static abstract class BinaryFeature extends Feature {

		private static final long serialVersionUID = 1L;
		private final FeatureKey defaultKey;
		private int defaultIndex = 0;
		private double defaultScore = Double.MIN_VALUE;

		BinaryFeature() {
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

		public int getFeatureIndex(final Category category, final RuleType ruleClass, final Category left,
				final RuleClass leftRuleClass, final int leftLength, final Category right,
				final RuleClass rightRuleClass,

				final int rightLength, final List<InputWord> sentence, final Map<FeatureKey, Integer> featureToIndex) {
			final FeatureKey featureKey = getFeatureKey(category, ruleClass, left, leftRuleClass, leftLength, right,
					rightRuleClass, rightLength, sentence);
			final Integer result = featureToIndex.get(featureKey);
			if (result == null) {
				if (defaultIndex == 0) {
					defaultIndex = featureToIndex.get(defaultKey);
				}

				return defaultIndex;
			}
			return result;
		}

		public abstract FeatureKey getFeatureKey(final Category category, final RuleType ruleClass,
				final Category left, final RuleClass leftRuleClass, final int leftLength, final Category right,
				final RuleClass rightRuleClass, int rightLength, List<InputWord> sentence);

		public double getFeatureScore(final Category category, final RuleType ruleClass, final Category left,
				final RuleClass leftRuleClass, final int leftLength, final Category right,
				final RuleClass rightRuleClass, final int rightLength, final List<InputWord> sentence,
				final ObjectDoubleHashMap<FeatureKey> featureToScore) {
			final FeatureKey featureKey = getFeatureKey(category, ruleClass, left, leftRuleClass, leftLength, right,
					rightRuleClass, rightLength, sentence);
			final double result = featureToScore.getOrDefault(featureKey, Double.MIN_VALUE);
			if (result == Double.MIN_VALUE) {
				if (defaultScore == Double.MIN_VALUE) {
					defaultScore = featureToScore.get(defaultKey);
				}

				return defaultScore;
			}
			return result;
		}

		private final static BinaryFeature leftAndRightFeature = new BinaryFeature() {
			private static final long serialVersionUID = 1L;

			@Override
			public FeatureKey getFeatureKey(final Category result, final RuleType ruleClass, final Category left,
					final RuleClass leftRuleClass, final int leftLength, final Category right,
					final RuleClass rightRuleClass, final int rightLength, final List<InputWord> sentence) {
				return hash(super.id, left.hashCode(), right.hashCode());
			}
		};

		@SuppressWarnings("unused")
		private final static BinaryFeature ruleFeature = new BinaryFeature() {
			private static final long serialVersionUID = 1L;

			@Override
			public FeatureKey getFeatureKey(final Category result, final RuleType ruleClass, final Category left,
					final RuleClass leftRuleClass, final int leftLength, final Category right,
					final RuleClass rightRuleClass, final int rightLength, final List<InputWord> sentence) {
				return hash(super.id, left.hashCode(), right.hashCode(), result.hashCode());
			}
		};

		@SuppressWarnings("unused")
		private final static BinaryFeature ruleClassFeature = new BinaryFeature() {
			private static final long serialVersionUID = 1L;

			@Override
			public FeatureKey getFeatureKey(final Category result, final RuleType ruleClass, final Category left,
					final RuleClass leftRuleClass, final int leftLength, final Category right,
					final RuleClass rightRuleClass, final int rightLength, final List<InputWord> sentence) {
				return hash(super.id, ruleClass.toString().hashCode());
			}
		};

		// Aimed at cases where a unary rule is used, and should apply nearby
		// (e.g. reduced relatives)
		@SuppressWarnings("unused")
		private final static BinaryFeature leftLengthFeature = new BinaryFeature() {
			private static final long serialVersionUID = 1L;

			@Override
			public FeatureKey getFeatureKey(final Category result, final RuleType ruleClass, final Category left,
					final RuleClass leftRuleClass, final int leftLength, final Category right,
					final RuleClass rightRuleClass, final int rightLength, final List<InputWord> sentence) {
				return hash(super.id, leftRuleClass.toString().hashCode(), left.hashCode(), rightLength);
			}
		};

		@SuppressWarnings("unused")
		private final static BinaryFeature rightLengthFeature = new BinaryFeature() {
			private static final long serialVersionUID = 1L;

			@Override
			public FeatureKey getFeatureKey(final Category result, final RuleType ruleClass, final Category left,
					final RuleClass leftRuleClass, final int leftLength, final Category right,
					final RuleClass rightRuleClass, final int rightLength, final List<InputWord> sentence) {
				return hash(super.id, rightRuleClass.toString().hashCode(), right.hashCode(), leftLength);
			}
		};

		public static Collection<BinaryFeature> getFeatures() {
			return Arrays.asList(
			// ruleClassFeature
					leftAndRightFeature);
		}
	}

}
