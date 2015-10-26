package edu.uw.easysrl.semantics;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import edu.uw.easysrl.semantics.Variable.VariableNames;

/**
 * Represents f(x), a function into an entity with its arguments. Very similar to an atomic sentence p(x), but this is
 * E-typed, not a Sentence. It'd be nice to share more code with AtomicSentence, but it's awkward because of multiple
 * inheritance issues.
 *
 */
public class Function extends Logic {

	private static final long serialVersionUID = 1L;
	private final List<? extends Logic> children;
	private final Logic function;

	@Override
	public Logic doSubstitution(final Substitution substitution) {
		final Logic newPredicate = function.doSubstitution(substitution);

		if (newPredicate.getArguments().size() > 0) {
			// Simplify (#x . p(x))(Y) to p(Y)

			Logic simplified = newPredicate;
			for (final Logic child : children) {
				simplified = simplified.apply(child.doSubstitution(substitution));
			}

			// Should have no arguments if the types were correct.
			Preconditions.checkState(simplified.getArguments().isEmpty());

			return simplified;
		} else {
			return new Function(newPredicate, children.stream().map(x -> x.doSubstitution(substitution))
					.collect(Collectors.toList()));
		}

	}

	public static Logic make(final Logic function, final List<? extends Logic> children) {
		if (children.size() == 0) {
			Preconditions.checkArgument(function.getType() == SemanticType.E);
			return function;
		} else {
			return new Function(function, children);
		}
	}

	private Function(final Logic predicate, final List<? extends Logic> children) {

		this.function = predicate;
		this.children = ImmutableList.copyOf(children);

		Preconditions.checkArgument(children.size() > 0);
		for (final Logic child : children) {
			Preconditions.checkNotNull(child);
		}
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {

		function.toString(result, varToName);
		result.append("(");
		boolean isFirst = true;
		for (final Logic child : children) {
			if (isFirst) {
				isFirst = false;
			} else {
				result.append(",");
			}
			child.toString(result, varToName);
		}
		result.append(")");
	}

	@Override
	protected Function alphaReduce(final Map<Variable, Variable> update) {
		return new Function(function.alphaReduce(update), children.stream().map(x -> x.alphaReduce(update))
				.collect(Collectors.toList()));
	}

	@Override
	public void accept(final LogicVisitor v) {
		v.visit(this);
	}

	public List<? extends Logic> getChildren() {
		return children;
	}

	public Logic getPredicate() {
		return function;
	}

	@Override
	public SemanticType getType() {
		return SemanticType.E;
	}
}
