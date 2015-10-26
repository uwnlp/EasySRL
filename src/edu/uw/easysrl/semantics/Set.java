package edu.uw.easysrl.semantics;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import edu.uw.easysrl.semantics.Variable.VariableNames;

/**
 * Represents a set of entities.
 *
 */
public class Set extends Logic {
	private static final long serialVersionUID = 1L;
	private final Collection<Logic> children;

	public Set(final Logic... children) {
		this(Arrays.asList(children));
	}

	public Set(final Collection<Logic> children) {
		this.children = children;
	}

	@Override
	public Logic doSubstitution(final Substitution substitution) {
		return new Set(children.stream().map(x -> x.doSubstitution(substitution)).collect(Collectors.toList()));
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {
		result.append("{");

		boolean isFirst = true;
		for (final Logic child : children) {
			if (isFirst) {
				isFirst = false;
			} else {
				result.append(", ");
			}
			child.toString(result, varToName);
		}
		result.append("}");
	}

	@Override
	public SemanticType getType() {
		return SemanticType.E;
	}

	@Override
	protected Logic alphaReduce(final Map<Variable, Variable> update) {
		return new Set(children.stream().map(x -> x.alphaReduce(update)).collect(Collectors.toList()));
	}

	@Override
	public void accept(final LogicVisitor v) {
		v.visit(this);
	}

	public Collection<Logic> getChildren() {
		return children;
	}
}
