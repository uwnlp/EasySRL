package edu.uw.easysrl.semantics;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uw.easysrl.semantics.Variable.VariableNames;

public abstract class Logic implements Serializable {

	private static final long serialVersionUID = 1L;

	public abstract Logic doSubstitution(Substitution substitution);

	@Override
	public String toString() {
		final StringBuilder result = new StringBuilder();
		toString(result, new VariableNames(true));
		return result.toString();
	}

	abstract void toString(StringBuilder result, VariableNames varToName);

	public Logic alphaReduce() {
		return alphaReduce(new HashMap<>());
	}

	protected abstract Logic alphaReduce(Map<Variable, Variable> update);

	/**
	 * Apply to another expression. Only supported for LambdaExpressions.
	 */
	public Logic apply(@SuppressWarnings("unused") final Logic argument) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Compose with another expression. Only supported for LambdaExpressions.
	 */
	public Logic compose(@SuppressWarnings("unused") final Logic argument) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Generalized composition (to degree 2). Only supported for LambdaExpressions.
	 */
	public Logic compose2(@SuppressWarnings("unused") final Logic argument) {
		throw new UnsupportedOperationException();
	}

	public List<Variable> getArguments() {
		return Collections.emptyList();
	}

	public abstract SemanticType getType();

	public abstract void accept(LogicVisitor v);

  public interface LogicVisitor {
		void visit(AtomicSentence s);

		void visit(ConnectiveSentence s);

		void visit(Constant s);

		void visit(QuantifierSentence s);

		void visit(OperatorSentence s);

		void visit(Set s);

		void visit(SkolemTerm s);

		void visit(Variable s);

		void visit(LambdaExpression lambdaExpression);

		void visit(Function function);
	}

}
