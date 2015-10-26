package edu.uw.easysrl.semantics;

import java.util.Map;

import edu.uw.easysrl.semantics.Variable.VariableNames;

/**
 * Steedman-style Generalized Skolem Term. sk(#x.p(x)) represents an entity x satisfying p. TODO proper treatment of
 * cardinality conditions
 *
 */
public class SkolemTerm extends Logic {
	private static final long serialVersionUID = 1L;

	private final String quantifier;
	private final LambdaExpression property;

	public SkolemTerm(final LambdaExpression property) {
		this(null, property);
	}

	private SkolemTerm(final String quantifier, final LambdaExpression property) {
		super();
		this.quantifier = quantifier;
		this.property = property;
	}

	@Override
	public Logic doSubstitution(final Substitution substitution) {
		return new SkolemTerm(quantifier, (LambdaExpression) property.doSubstitution(substitution));
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {

		result.append("sk(");
		property.toString(result, varToName);
		if (quantifier != null) {
			result.append(";");
			result.append(quantifier);
		}
		result.append(")");
	}

	@Override
	public SemanticType getType() {
		return SemanticType.E;
	}

	@Override
	protected Logic alphaReduce(final Map<Variable, Variable> update) {
		return new SkolemTerm(quantifier, property.alphaReduce(update));
	}

	@Override
	public void accept(final LogicVisitor v) {
		v.visit(this);
	}

	public LambdaExpression getCondition() {
		return property;
	}
}
