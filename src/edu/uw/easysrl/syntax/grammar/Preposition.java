package edu.uw.easysrl.syntax.grammar;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Preposition implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private final static AtomicInteger numPreps = new AtomicInteger();

	private final static Map<String, Preposition> cache = Collections
			.synchronizedMap(new HashMap<>());

	public final static Preposition AS = make("AS");
	public final static Preposition AT = make("AT");
	public final static Preposition BY = make("BY");
	public final static Preposition FOR = make("FOR");
	public final static Preposition FROM = make("FROM");
	public final static Preposition IN = make("IN");
	public final static Preposition OF = make("OF");
	public final static Preposition ON = make("ON");
	public final static Preposition TO = make("TO");
	public final static Preposition WITH = make("WITH");
	public final static Preposition UNSPECIFIED = make("UNSPECIFIED");
	public final static Preposition OTHER = make("OTHER");
	public final static Preposition NONE = make("NONE");

	private final String name;
	private final int id;

	private Preposition(final String name) {
		this.name = name;
		this.id = numPreps.getAndIncrement();
	}

	private static Preposition make(final String preposition) {
		Preposition result = cache.get(preposition);
		if (result == null) {
			result = new Preposition(preposition);
			cache.put(preposition, result);
		}

		return result;
	}

	public static int numberOfPrepositions() {
		return numPreps.get();
	}

	/**
	 * A unique numeric identifier. NOT guaranteed to be stable across JVMs.
	 */
	public int getID() {
		return id;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public boolean equals(final Object other) {
		return this == other;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public static Collection<Preposition> values() {
		return cache.values();
	}

	private Object readResolve() {
		return make(this.name);
	}

	/*
	 * ABOUT, AGAINST, AMONG, AS, AT, BETWEEN, BY, FOR, FROM, IN, INTO, LIKE,
	 * OF, ON, OVER, THROUGH, TO, UNDER, WITH
	 */

	public static Category subcategorize(Category category, final String word) {
		if (category.isFunctionInto(Category.PP)
				&& !category.isFunctionIntoModifier()) {
			category = category
					.replaceArgument(0, Category.PP.addFeature(word));
		} else if (category.equals(Category.POSSESSIVE_ARGUMENT)) {
			// (NP/(N/PP))\NP
			category = Category.valueOf("(NP/(N/PP[poss]))\\NP");
		} else if (category.equals(Category.POSSESSIVE_PRONOUN)) {
			// NP/(N/PP)
			category = Category.valueOf("NP/(N/PP[poss])");
		}

		return category;
	}

	public static Preposition fromString(String preposition) {
		if (preposition == null) {
			return Preposition.NONE;
		} else {
			preposition = preposition.toUpperCase();
			for (final Preposition prep : values()) {
				if (prep.toString().toUpperCase().equals(preposition)) {
					return prep;
				}
			}

			return OTHER;
		}
	}

	public static boolean isPrepositionCategory(final Category category) {
		if (category.isFunctionInto(Category.PP)
				&& !category.isFunctionIntoModifier()) {
			return true;
		} else if (category.equals(Category.POSSESSIVE_ARGUMENT)
				|| category.equals(Category.POSSESSIVE_PRONOUN)) {
			return true;
		}

		return false;
	}

}

/*
 * about against among as at between by for from in into like of on over through
 * to under with
 * 
 * PARTICLES away back down in off on out over up
 */