package edu.uw.easysrl.semantics;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import edu.uw.easysrl.semantics.Variable.VariableNames;

public class AtomicSentence extends Sentence {

	private static final long serialVersionUID = 1L;
	private final List<? extends Logic> children;
	private final Logic predicate;

	@Override
	public Sentence doSubstitution(final Substitution substitution) {
		final Logic newPredicate = predicate.doSubstitution(substitution);

		if (newPredicate.getArguments().size() > 0) {
			// Simplify (#x . p(x))(Y) to p(Y)

			Logic simplified = newPredicate;
			for (final Logic child : children) {
				simplified = simplified.apply(child.doSubstitution(substitution));
			}

			// Should have no arguments if the types were correct.
			Preconditions.checkState(simplified.getArguments().isEmpty());

			return (Sentence) simplified;
		} else {
			return new AtomicSentence(newPredicate, children.stream().map(x -> x.doSubstitution(substitution))
					.collect(Collectors.toList()));
		}

	}

	/**
	 * Works out the semantic type of a predicate, given its arguments
	 */
	private static SemanticType getType(final List<? extends Logic> arguments) {
		SemanticType type = SemanticType.T;
		for (int i = arguments.size() - 1; i >= 0; i--) {
			type = SemanticType.make(arguments.get(i).getType(), type);
		}
		return type;
	}

	public AtomicSentence(final String predicate, final Logic... children) {
		this(predicate, Arrays.asList(children));
	}

	AtomicSentence(final String predicate, final List<Logic> children) {
		this(new Constant(predicate, getType(children)), children);
	}

	public AtomicSentence(final Logic predicate, final Logic... children) {
		this(predicate, Arrays.asList(children));
	}

	public AtomicSentence(final Logic predicate, final List<? extends Logic> children) {

		this.predicate = predicate;
		this.children = ImmutableList.copyOf(children);

		Preconditions.checkArgument(children.size() > 0);
		for (final Logic child : children) {
			Preconditions.checkNotNull(child);
		}
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {

		predicate.toString(result, varToName);
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
	protected Sentence alphaReduce(final Map<Variable, Variable> update) {
		return new AtomicSentence(predicate.alphaReduce(update), children.stream().map(x -> x.alphaReduce(update))
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
		return predicate;
	}
}
