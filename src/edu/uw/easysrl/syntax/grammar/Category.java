package edu.uw.easysrl.syntax.grammar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.ImmutableSet;

import edu.uw.easysrl.util.Util;

public abstract class Category implements Serializable, Comparable<Category> {

	private static final long serialVersionUID = -9163900631701144973L;
	private final String asString;
	private final int id;
	private final static String WILDCARD_FEATURE = "X";
	private final static Set<String> bracketAndQuoteCategories = ImmutableSet.of("LRB", "RRB", "LQU", "RQU");
	private final static AtomicInteger numCats = new AtomicInteger();

	private final static Map<String, Category> cache = new HashMap<>();

	public static final Category COMMA = valueOf(",");
	public static final Category CONJ = valueOf("conj");
	public static final Category LQU = valueOf("LQU");
	public static final Category RQU = valueOf("RQU");
	public static final Category LRB = valueOf("LRB");
	public final static Category N = valueOf("N");
	public static final Category NP = valueOf("NP");
	public static final Category NPthr = valueOf("NP[thr]");
	public static final Category NPexpl = valueOf("NP[expl]");
	public static final Category PP = valueOf("PP");
	public static final Category PR = valueOf("PR");
	public static final Category PREPOSITION = valueOf("PP/NP");
	public static final Category SEMICOLON = valueOf(";");
	public static final Category Sdcl = valueOf("S[dcl]");
	public static final Category Sq = valueOf("S[q]");
	public static final Category Swq = valueOf("S[wq]");
	public static final Category ADVERB = valueOf("(S\\NP)\\(S\\NP)");
	public static final Category POSSESSIVE_ARGUMENT = valueOf("(NP/(N/PP))\\NP");
	public static final Category POSSESSIVE_PRONOUN = valueOf("NP/(N/PP)");
	public static final Category S = valueOf("S");
	public static final Category ADJECTIVE = valueOf("N/N");
	public static final Category DETERMINER = Category.valueOf("NP[nb]/N");

	private Category(final String asString) {
		this.asString = asString;
		this.id = numCats.getAndIncrement();
	}

	public static int numberOfCategories() {
		return numCats.get();
	}

	public enum Slash {
		FWD, BWD, EITHER;
		@Override
		public String toString() {
			String result = "";

			switch (this) {
			case FWD:
				result = "/";
				break;
			case BWD:
				result = "\\";
				break;
			case EITHER:
				result = "|";
				break;

			}

			return result;
		}

		public static Slash fromString(final String text) {
			if (text != null) {
				for (final Slash slash : values()) {
					if (text.equalsIgnoreCase(slash.toString())) {
						return slash;
					}
				}
			}
			throw new IllegalArgumentException("Invalid slash: " + text);
		}

		public boolean matches(final Slash other) {
			return this == EITHER || this == other;
		}
	}

	public static Category valueOf(final String cat) {

		// Tries to guarantee that equal categories are identical objects.
		// TODO simplify this
		Category result = cache.get(cat);
		if (result == null) {

			String name;
			name = dropMarkup(cat);
			name = Util.dropBrackets(name);
			result = cache.get(name);

			if (result == null) {
				result = Category.valueOfUncached(name);

				synchronized (cache) {
					final Category existing = cache.get(result.asString);
					if (existing != null) {
						result = existing;
					} else {
						cache.put(result.asString, result);
					}
				}

				if (name != cat) {
					synchronized (cache) {
						cache.put(name, result);
					}
				}
			}

			synchronized (cache) {
				cache.put(cat, result);
			}
		}

		return result;
	}

	private static String dropMarkup(final String withMarkUp) {
		String withoutMarkup = withMarkUp.replaceAll("_[0-9]+", "");
		withoutMarkup = withoutMarkup.replaceAll(":B", "");
		withoutMarkup = withoutMarkup.replaceAll("\\{_\\*\\}", "");
		return withoutMarkup;
	}

	/**
	 * Builds a category from a string representation.
	 */
	private static Category valueOfUncached(final String source) {
		// Categories have the form: ((X/Y)\Z[feature]){ANNOTATION}
		String newSource = source;

		if (newSource.startsWith("(")) {
			final int closeIndex = Util.findClosingBracket(newSource);

			if (Util.indexOfAny(newSource.substring(closeIndex), "/\\|") == -1) {
				// Simplify (X) to X
				newSource = newSource.substring(1, closeIndex);
				final Category result = valueOf(newSource);

				return result;
			}
		}

		final int endIndex = newSource.length();

		final int opIndex = Util.findNonNestedChar(newSource, "/\\|");

		if (opIndex == -1) {
			// Atomic Category
			int featureIndex = newSource.indexOf("[");
			final List<String> features = new ArrayList<>();

			final String base = (featureIndex == -1 ? newSource : newSource.substring(0, featureIndex));

			while (featureIndex > -1) {
				features.add(newSource.substring(featureIndex + 1, newSource.indexOf("]", featureIndex)));
				featureIndex = newSource.indexOf("[", featureIndex + 1);
			}

			if (features.size() > 1) {
				throw new RuntimeException("Can only handle single features: " + source);
			}

			final String feature = features.size() == 0 ? null : features.get(0);
			final Category c = new AtomicCategory(base, feature);
			return c;
		} else {
			// Functor Category

			final Category left = valueOf(newSource.substring(0, opIndex));
			final Category right = valueOf(newSource.substring(opIndex + 1, endIndex));
			return new FunctorCategory(left, Slash.fromString(newSource.substring(opIndex, opIndex + 1)), right);
		}
	}

	@Override
	public String toString() {
		return asString;
	}

	@Override
	public boolean equals(final Object other) {
		return this == other;// || toString().equals(other.toString());
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	public abstract boolean isTypeRaised();

	public abstract boolean isForwardTypeRaised();

	public abstract boolean isBackwardTypeRaised();

	public abstract boolean isModifier();

	public abstract boolean matches(Category other);

	public abstract Category getLeft();

	public abstract Category getRight();

	public abstract Slash getSlash();

	abstract String getFeature();

	abstract String toStringWithBrackets();

	public abstract Category dropPPandPRfeatures();

	private static class FunctorCategory extends Category {

		private static final long serialVersionUID = 2140839570125932441L;
		private final Category left;
		private final Category right;
		private final Slash slash;
		private final boolean isMod;

		private FunctorCategory(final Category left, final Slash slash, final Category right) {
			super(left.toStringWithBrackets() + slash + right.toStringWithBrackets());
			this.left = left;
			this.right = right;
			this.slash = slash;
			this.isMod = left.equals(right);

			// X|(X|Y)
			this.isTypeRaised = right.isFunctor() && right.getLeft().equals(left);
		}

		/**
		 * After deserialization, re-create the object so that we don't have multiple copies around.
		 *
		 * @return
		 */
		private Object readResolve() {
			return Category.valueOf(this.toString());
		}

		@Override
		public boolean isModifier() {
			return isMod;
		}

		@Override
		public boolean matches(final Category other) {
			return other.isFunctor() && left.matches(other.getLeft()) && right.matches(other.getRight())
					&& slash.matches(other.getSlash());
		}

		@Override
		public Category getLeft() {
			return left;
		}

		@Override
		public Category getRight() {
			return right;
		}

		@Override
		public Slash getSlash() {
			return slash;
		}

		@Override
		String getFeature() {
			throw new UnsupportedOperationException();
		}

		@Override
		String toStringWithBrackets() {
			return "(" + toString() + ")";
		}

		@Override
		public boolean isFunctor() {
			return true;
		}

		@Override
		public boolean isPunctuation() {
			return false;
		}

		@Override
		String getType() {
			throw new UnsupportedOperationException();
		}

		@Override
		String getSubstitution(final Category other) {
			String result = getRight().getSubstitution(other.getRight());
			if (result == null) {
				// Bit of a hack, but seems to reproduce CCGBank in cases of
				// clashing features.
				result = getLeft().getSubstitution(other.getLeft());
			}
			return result;
		}

		private final boolean isTypeRaised;

		@Override
		public boolean isTypeRaised() {
			return isTypeRaised;
		}

		@Override
		public boolean isForwardTypeRaised() {
			// X/(X\Y)
			return isTypeRaised() && getSlash() == Slash.FWD;
		}

		@Override
		public boolean isBackwardTypeRaised() {
			// X\(X/Y)
			return isTypeRaised() && getSlash() == Slash.BWD;
		}

		@Override
		boolean isNounOrNP() {
			return false;
		}

		@Override
		public Category addFeature(final String preposition) {
			throw new RuntimeException("Functor categories cannot take features");
		}

		private int numberOfArguments = 0;

		@Override
		public int getNumberOfArguments() {
			if (numberOfArguments == 0) {
				numberOfArguments = 1 + left.getNumberOfArguments();
			}
			return numberOfArguments;
		}

		@Override
		public Category replaceArgument(final int argNumber, final Category newCategory) {
			if (argNumber == getNumberOfArguments()) {
				return newCategory != null ? Category.make(left, slash, newCategory) : left;
			} else {
				return Category.make(left.replaceArgument(argNumber, newCategory), slash, right);
			}
		}

		@Override
		public Category getArgument(final int argNumber) {
			if (argNumber == getNumberOfArguments()) {
				return right;
			} else {
				return left.getArgument(argNumber);
			}
		}

		@Override
		public Category getHeadCategory() {
			return left.getHeadCategory();
		}

		@Override
		public boolean isFunctionIntoModifier() {
			return isModifier() || left.isModifier();
		}

		@Override
		public boolean isFunctionInto(final Category into) {
			return into.matches(this) || left.isFunctionInto(into);
		}

		@Override
		public Category dropPPandPRfeatures() {
			return Category.make(left.dropPPandPRfeatures(), slash, right.dropPPandPRfeatures());
		}

		@Override
		public Category addArgument(final int argNumber, final Category newArg) {
			if (argNumber == getNumberOfArguments() + 1) {
				return Category.make(this, Slash.FWD, newArg);
			} else {
				return Category.make(left.addArgument(argNumber, newArg), slash, right);
			}
		}
	}

	abstract String getSubstitution(Category other);

	private static class AtomicCategory extends Category {

		private static final long serialVersionUID = -1021124001104979306L;

		private AtomicCategory(final String type, final String feature) {
			super(type + (feature == null ? "" : "[" + feature + "]"));
			this.type = type;
			this.feature = feature;
			isPunctuation = !type.matches("[A-Za-z]+") || bracketAndQuoteCategories.contains(type);
		}

		private final String type;
		private final String feature;

		@Override
		public boolean isModifier() {
			return false;
		}

		@Override
		public boolean matches(final Category other) {
			return !other.isFunctor()
					&& type.equals(other.getType())
					&& (feature == null || feature.equals(other.getFeature()) || WILDCARD_FEATURE.equals(getFeature())
							|| WILDCARD_FEATURE.equals(other.getFeature()) || feature.equals("nb") // Ignoring the
					// NP[nb] feature,
					// which isn't very helpful. For
					// example, it stops us
					// coordinating
					// "John and a girl",
					// because "and a girl" ends up with a NP[nb]\NP[nb] tag.

					);
		}

		/**
		 * After deserialization, re-create the object so that we don't have multiple copies around.
		 *
		 * @return
		 */
		private Object readResolve() {
			return Category.valueOf(this.toString());
		}

		@Override
		public Category getLeft() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Category getRight() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Slash getSlash() {
			throw new UnsupportedOperationException();
		}

		@Override
		String getFeature() {
			return feature;
		}

		@Override
		String toStringWithBrackets() {
			return toString();
		}

		@Override
		public boolean isFunctor() {
			return false;
		}

		private final boolean isPunctuation;

		@Override
		public boolean isPunctuation() {
			return isPunctuation;
		}

		@Override
		String getType() {
			return type;
		}

		@Override
		String getSubstitution(final Category other) {
			if (WILDCARD_FEATURE.equals(getFeature())) {
				return other.getFeature();
			} else if (WILDCARD_FEATURE.equals(other.getFeature())) {
				return feature;
			}
			return null;
		}

		@Override
		public boolean isTypeRaised() {
			return false;
		}

		@Override
		public boolean isForwardTypeRaised() {
			return false;
		}

		@Override
		public boolean isBackwardTypeRaised() {
			return false;
		}

		@Override
		boolean isNounOrNP() {
			return type.equals("N") || type.equals("NP");
		}

		@Override
		public Category addFeature(String newFeature) {
			if (feature != null) {
				throw new RuntimeException("Only one feature allowed. Can't add feature: " + newFeature
						+ " to category: " + this);
			}
			newFeature = newFeature.replaceAll("/", "");
			newFeature = newFeature.replaceAll("\\\\", "");
			return valueOf(type + "[" + newFeature + "]");
		}

		@Override
		public int getNumberOfArguments() {
			return 0;
		}

		@Override
		public Category replaceArgument(final int argNumber, final Category newCategory) {
			if (argNumber == 0) {
				return newCategory;
			}
			throw new RuntimeException("Error replacing argument of category");
		}

		@Override
		public Category getArgument(final int argNumber) {
			if (argNumber == 0) {
				return this;
			}
			throw new RuntimeException("Error getting argument of category");
		}

		@Override
		public Category getHeadCategory() {
			return this;
		}

		@Override
		public boolean isFunctionIntoModifier() {
			return false;
		}

		@Override
		public boolean isFunctionInto(final Category into) {
			return into.matches(this);
		}

		@Override
		public Category dropPPandPRfeatures() {
			if (type.equals("PP") || type.equals("PR")) {
				return valueOf(type);
			} else {
				return this;
			}
		}

		@Override
		public Category addArgument(final int argNumber, final Category newArg) {
			if (argNumber == 0) {
				return newArg;
			}
			throw new RuntimeException("Error replacing argument of category");

		}
	}

	public static Category make(final Category left, final Slash op, final Category right) {
		return valueOf(left.toStringWithBrackets() + op + right.toStringWithBrackets());
	}

	abstract String getType();

	abstract boolean isFunctor();

	public abstract boolean isPunctuation();

	/**
	 * Returns the Category created by substituting all [X] wildcard features with the supplied argument.
	 */
	Category doSubstitution(final String substitution) {
		if (substitution == null) {
			return this;
		}
		return valueOf(toString().replaceAll(WILDCARD_FEATURE, substitution));
	}

	/**
	 * A unique numeric identifier. NOT guaranteed to be stable across JVMs.
	 */
	public int getID() {
		return id;
	}

	abstract boolean isNounOrNP();

	public abstract Category addFeature(String preposition);

	public abstract int getNumberOfArguments();

	public abstract Category getHeadCategory();

	public abstract boolean isFunctionInto(Category into);

	/**
	 * Replaces the argument with the given index with the new Category. Arguments are count from the left, starting
	 * with 0 for the head Category. e.g. (((S\NP)/NP)/PP)/PR @replaceArgument(3, PP[in]) = (((S\NP)/NP)/PP[in])/PR
	 */
	public abstract Category replaceArgument(int argNumber, Category newCategory);

	public abstract Category getArgument(int argNumber);

	public abstract boolean isFunctionIntoModifier();

	public abstract Category addArgument(int argNumber, Category newArg);

	public Category addAnnotation(final String annotation) {
		return valueOf(toString() + "{" + annotation + "}");
	}

	public Category withoutAnnotation() {
		return this;
	}

	@Override
	/**
	 * Implementing Comparable for use in TreeMaps.
	 */
	public int compareTo(final Category other) {
		return id - other.id;
	}

}