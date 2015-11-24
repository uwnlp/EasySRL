package edu.uw.easysrl.semantics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import edu.uw.easysrl.semantics.Variable.VariableNames;
import edu.uw.easysrl.util.Util;

public class LambdaExpression extends Logic {
	private static final long serialVersionUID = 1L;

	private final List<Variable> vars;
	private final Logic statement;

	public LambdaExpression(final Logic statement, final Variable... vars) {
		this(statement, Arrays.asList(vars));
	}

	private LambdaExpression(final Logic statement, final List<Variable> vars) {
		super();
		Preconditions.checkArgument(statement != null);
		for (final Variable v : vars) {
			Preconditions.checkArgument(v != null);
		}
		this.statement = statement;
		// Because subList isn't serializable.
		this.vars = ImmutableList.copyOf(vars);
	}

	@Override
	public Logic apply(final Logic other) {
		return make(statement.doSubstitution(new Substitution(vars.get(0), other)), vars.subList(1, vars.size()));
	}

	@Override
	public Logic compose(final Logic other) {
		// g . f = #z . g(f(z))
		final Variable z = new Variable(other.getArguments().get(0).getType());
		final Logic fz = other.apply(z);
		return make(apply(fz), Arrays.asList(z));
	}

	@Override
	public Logic compose2(final Logic other) {
		// g .2 f = #z1#z2 . g(f(z2, z1))
		final Variable z1 = new Variable(other.getArguments().get(0).getType());
		final Variable z2 = new Variable(other.getArguments().get(1).getType());
		final Logic fz = other.apply(z2).apply(z1);
		return make(apply(fz), Arrays.asList(z2, z1));
	}

	public static Logic make(final Logic statement, final Variable... vars) {
		return make(statement, Arrays.asList(vars));
	}

	public static Logic make(final Logic statement, final List<Variable> vars) {
		if (vars.size() == 0) {
			// Simplify no arguments
			return statement;
		} else if (statement.getArguments().size() > 0) {
			// Simplify #x . # y . foo(x,y)
			final List<Variable> newArguments = new ArrayList<>(vars.size() + statement.getArguments().size());
			newArguments.addAll(vars);
			newArguments.addAll(statement.getArguments());
			return new LambdaExpression(((LambdaExpression) statement).statement, newArguments);

		} else {
			return new LambdaExpression(statement, vars);
		}
	}

	@Override
	public Logic doSubstitution(final Substitution substitution) {
		if (substitution.containsAny(vars)) {
			Util.debugHook();
		}
		Preconditions.checkArgument(!substitution.containsAny(vars));
		return new LambdaExpression(statement.doSubstitution(substitution), vars);
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {
		for (int i = 0; i < vars.size(); i++) {
			result.append("#");
			vars.get(i).toString(result, varToName);
		}
		result.append(".");
		statement.toString(result, varToName);
	}

	@Override
	public List<Variable> getArguments() {
		return vars;
	}

	@Override
	public SemanticType getType() {
		SemanticType result = statement.getType();
		for (int i = vars.size() - 1; i >= 0; i--) {
			result = SemanticType.make(vars.get(i).getType(), result);
		}
		return result;
	}

	public Logic getStatement() {
		return statement;
	}

	@Override
	protected LambdaExpression alphaReduce(final Map<Variable, Variable> update) {
		final List<Variable> newVars = vars.stream().map(x -> x.alphaReduce(update)).collect(Collectors.toList());
		return new LambdaExpression(statement.alphaReduce(update), newVars);
	}

	@Override
	public void accept(final LogicVisitor v) {
		v.visit(this);
	}
}
