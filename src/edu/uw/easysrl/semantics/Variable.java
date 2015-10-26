package edu.uw.easysrl.semantics;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public class Variable extends Logic {
	private static final long serialVersionUID = 1L;

	private final SemanticType type;

	public Variable(final SemanticType type) {
		super();
		this.type = type;
	}

	@Override
	public Logic doSubstitution(final Substitution substitution) {
		final Logic result = substitution.getValue(this);
		if (result == null) {
			return this;
		} else {
			return result;
		}
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {
		result.append(varToName.getName(this));
	}

	@Override
	public SemanticType getType() {
		return type;
	}

	@Override
	protected Variable alphaReduce(final Map<Variable, Variable> update) {
		Variable result = update.get(this);
		if (result == null) {
			result = new Variable(type);
			update.put(this, result);
		}
		return result;
	}

	/**
	 * Keeps track of names of variables, for pretty printing.
	 */
	static class VariableNames {
		private final Map<Variable, String> varToName = new HashMap<>();
		private final Multiset<String> countForLetter = HashMultiset.create(3);
		private final boolean usePrimes;

		/**
		 * If use primes, variables are x, x', x''. Otherwise: x0, x1, x2
		 */
		VariableNames(final boolean usePrimes) {
			super();
			this.usePrimes = usePrimes;
		}

		private String getName(final Variable variable) {
			String result = varToName.get(variable);
			if (result == null) {
				String names;
				if (variable.getType() == SemanticType.E) {
					names = "xyzuv";
				} else if (variable.getType() == SemanticType.Ev) {
					names = "e";
				} else {
					names = "pqr";
				}
				final int number = countForLetter.count(names);
				countForLetter.add(names);

				//
				final StringBuilder name = new StringBuilder();
				name.append(names.charAt(number % names.length()));
				if (usePrimes) {
					for (int i = 0; i < number / names.length(); i++) {
						name.append("'");
					}
				} else {
					name.append(number);
				}

				result = name.toString();
				varToName.put(variable, result);
			}

			return result;
		}
	}

	@Override
	public void accept(final LogicVisitor v) {
		v.visit(this);
	}
}
