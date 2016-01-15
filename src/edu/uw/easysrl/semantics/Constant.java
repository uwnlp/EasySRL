package edu.uw.easysrl.semantics;

import java.util.Map;

import edu.uw.easysrl.semantics.Variable.VariableNames;

public class Constant extends Logic {
	private static final long serialVersionUID = 1L;

	private final String name;
	private final SemanticType type;

	public Constant(final String name, final SemanticType type) {
		super();
		// TODO Preconditions.checkNotNull(name);
		this.name = name;
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public SemanticType getSemanticType() {
		return type;
	}

	@Override
	public Logic doSubstitution(final Substitution substitution) {
		return this;
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {
		result.append(name);
	}

	@Override
	public SemanticType getType() {
		return type;
	}

	@Override
	protected Logic alphaReduce(final Map<Variable, Variable> update) {
		return this;
	}

	@Override
	public void accept(final LogicVisitor v) {
		v.visit(this);
	}
}
