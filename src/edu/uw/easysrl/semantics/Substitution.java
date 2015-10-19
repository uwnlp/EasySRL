package edu.uw.easysrl.semantics;

import java.util.List;

/**
 * Stores variable/value pair for substitutions. Making a class for this in case we want to support multiple
 * substitutions.
 */
class Substitution {

	Substitution(final Variable variable, final Logic value) {
		this.var = variable;
		this.value = value;
	}

	private final Variable var;
	private final Logic value;

	Logic getValue(final Variable variable) {
		return variable == var ? value : variable;
	}

	boolean containsAny(final List<Variable> vars) {
		return vars.contains(var);
	}
}
