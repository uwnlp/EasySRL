package edu.uw.easysrl.semantics;

import java.util.Map;

import edu.uw.easysrl.semantics.Variable.VariableNames;

/**
 * Negation, modality, etc.
 */
public class OperatorSentence extends Sentence {
	private static final long serialVersionUID = 1L;
	private final Sentence child;
	private final Operator operator;

	public enum Operator {
		NOT("\u00AC"), MIGHT("\u22C4"), MUST("\u25FB");
		private final String asString;

		private Operator(final String asString) {
			this.asString = asString;
		}

		public String asString() {
			return asString;
		}

	}

	public OperatorSentence(final Operator operator, final Sentence child) {
		this.child = child;
		this.operator = operator;
	}

	@Override
	public Sentence doSubstitution(final Substitution substitution) {
		return new OperatorSentence(operator, child.doSubstitution(substitution));
	}

	@Override
	protected Sentence alphaReduce(final Map<Variable, Variable> update) {
		return new OperatorSentence(operator, child.alphaReduce(update));
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {
		result.append(operator.asString);
		final boolean bracket = child instanceof ConnectiveSentence;
		if (bracket) {
			result.append("(");
		}
		child.toString(result, varToName);
		if (bracket) {
			result.append(")");
		}
	}

	@Override
	public void accept(final LogicVisitor v) {
		v.visit(this);
	}

	public Operator getOperator() {
		return operator;
	}

	public Sentence getScope() {
		return child;
	}
}
