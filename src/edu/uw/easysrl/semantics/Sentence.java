package edu.uw.easysrl.semantics;

import java.util.Map;

public abstract class Sentence extends Logic {
	@Override
	public abstract Sentence doSubstitution(final Substitution substitution);

	@Override
	public SemanticType getType() {
		return SemanticType.T;
	}

	@Override
	protected abstract Sentence alphaReduce(final Map<Variable, Variable> update);
}
