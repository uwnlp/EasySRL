package edu.uw.easysrl.semantics;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.Util;

/**
 * Represents the semantic type of logical expressions, using entities (E), booleans (T) and events (Ev).
 *
 */
public abstract class SemanticType {
	private final static Table<SemanticType, SemanticType, SemanticType> cache = HashBasedTable.create();
	public final static SemanticType E = new AtomicSemanticType("E");
	public final static SemanticType T = new AtomicSemanticType("T");
	public static final SemanticType EtoT = make(E, T);
	public final static SemanticType Ev = new AtomicSemanticType("Ev");
	public static final SemanticType EventToT = make(Ev, T);
	private final static Map<Category, SemanticType> categoryToTypeCache = new HashMap<>();

	static class AtomicSemanticType extends SemanticType {
		private final String type;

		public AtomicSemanticType(final String type) {
			super();
			this.type = type;
		}

		@Override
		public SemanticType getFrom() {
			throw new UnsupportedOperationException();
		}

		@Override
		public SemanticType getTo() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isComplex() {
			return false;
		}

		@Override
		public String toString() {
			return type;
		}
	}

	static class ComplexSemanticType extends SemanticType {
		private final SemanticType from;
		private final SemanticType to;

		private ComplexSemanticType(final SemanticType from, final SemanticType to) {
			super();
			this.from = from;
			this.to = to;
		}

		@Override
		public SemanticType getFrom() {
			return from;
		}

		@Override
		public SemanticType getTo() {
			return to;
		}

		@Override
		public boolean isComplex() {
			return true;
		}

		@Override
		public String toString() {
			final StringBuilder result = new StringBuilder();
			result.append(Util.maybeBracket(from.toString(), from.isComplex()));
			result.append("->");
			result.append(Util.maybeBracket(to.toString(), to.isComplex()));
			return result.toString();
		}
	}

	/**
	 * Works out the semantic type of a category. e.g. (S\NP)/NP is E->(E->(Ev->T))
	 */
	public static SemanticType makeFromCategory(final Category c) {
		SemanticType result = categoryToTypeCache.get(c);

		if (result == null) {
			if (c.getNumberOfArguments() == 0) {
				// Atomic
				if (Category.N.matches(c)) {
					result = EtoT;
				} else if (Category.S.matches(c)) {
					result = EventToT;
				} else {
					result = E;
				}
			} else {
				// Complex
				final Category left = c.getLeft();
				final Category right = c.getRight();
				result = make(makeFromCategory(right), makeFromCategory(left));
			}
			categoryToTypeCache.put(c, result);
		}

		return result;
	}

	public static SemanticType make(final SemanticType from, final SemanticType to) {
		SemanticType result = cache.get(from, to);
		if (result == null) {
			result = new ComplexSemanticType(from, to);
			cache.put(from, to, result);
		}
		return result;
	}

	public abstract SemanticType getFrom();

	public abstract SemanticType getTo();

	public abstract boolean isComplex();

}
