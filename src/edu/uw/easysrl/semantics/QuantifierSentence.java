package edu.uw.easysrl.semantics;

import java.util.Map;

import edu.uw.easysrl.semantics.Variable.VariableNames;

public class QuantifierSentence extends Sentence {
	private static final long serialVersionUID = 1L;

	public enum Quantifier {

		EXISTS("\u2203", "&exist;"), FORALL("\u2200", "&forall;");
		private final String symbol;
		private final String html;

		private Quantifier(final String symbol, final String html) {
			this.symbol = symbol;
			this.html = html;
		}

		public String getSymbol() {
			return symbol;
		}

		public String asHTML() {
			return html;
		}
	}

	private final Quantifier quantifier;
	private final Variable variable;
	private final Sentence child;

	public QuantifierSentence(final Quantifier quantifier, final Variable variable, final Sentence child) {
		super();
		this.quantifier = quantifier;
		this.variable = variable;
		this.child = child;
	}

	@Override
	public Sentence doSubstitution(final Substitution substitution) {
		return new QuantifierSentence(quantifier, (Variable) variable.doSubstitution(substitution),
				child.doSubstitution(substitution));
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {
		result.append(quantifier.symbol);
		variable.toString(result, varToName);
		result.append("[");
		child.toString(result, varToName);
		result.append("]");

	}

	@Override
	protected Sentence alphaReduce(final Map<Variable, Variable> update) {
		final Variable newVar = variable.alphaReduce(update);
		return new QuantifierSentence(quantifier, newVar, child.alphaReduce(update));
	}

	@Override
	public void accept(final LogicVisitor v) {
		v.visit(this);
	}

	public Quantifier getQuantifier() {
		return quantifier;
	}

	public Variable getVariable() {
		return variable;
	}

	public Sentence getChild() {
		return child;
	}
}
