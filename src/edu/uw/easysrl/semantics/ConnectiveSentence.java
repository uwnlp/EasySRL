package edu.uw.easysrl.semantics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;

import edu.uw.easysrl.semantics.Variable.VariableNames;

public class ConnectiveSentence extends Sentence {
	private static final long serialVersionUID = 1L;
	private final Connective connective;
	private final List<Sentence> children;

	public enum Connective {
		AND("&"), OR("|"), IMPLIES("->");

		private final String asString;

		private Connective(final String asString) {
			this.asString = asString;
		}

		static Connective fromString(final String input) {
			for (final Connective c : values()) {
				if (input.equals(c.toString()) || input.equals(c.asString)) {
					return c;
				}
			}

			throw new IllegalArgumentException("No connective matching: " + input);
		}

	}

	@Override
	public Sentence doSubstitution(final Substitution substitution) {
		return ConnectiveSentence.make(connective,
				children.stream().map(x -> x.doSubstitution(substitution)).collect(Collectors.toList()));
	}

	private ConnectiveSentence(final Connective connective, final List<Sentence> children) {
		this.connective = connective;
		this.children = children;

		for (final Sentence child : children) {
			Preconditions.checkNotNull(child);
		}
	}

	private static Sentence make(final Connective connective, final List<Sentence> children2) {
		// Allow trailing nulls, for convenience
		final List<Sentence> children = new ArrayList<>();
		for (final Sentence child : children2) {
			if (child != null) {
				if (child instanceof ConnectiveSentence) {
					// Simplify nested ConnectiveSentences
					// e.g. p&(q&r) ----> p&q&r
					final ConnectiveSentence cs = (ConnectiveSentence) child;
					if (cs.getConnective() == connective) {
						children.addAll(((ConnectiveSentence) child).children);
					} else {
						children.add(child);
					}
				} else {
					children.add(child);
				}
			}
		}

		if (children.size() == 1) {
			return children.get(0);
		} else {
			Preconditions.checkArgument(children.size() > 1);

			return new ConnectiveSentence(connective, children);
		}
	}

	/**
	 * Conjoin a list of sentences. For convenience, these are allowed to be null (null sentences are ignored from the
	 * conjunction.) There must be at least one non-null argument.
	 */
	public static Sentence make(final Connective connective, final Sentence... children2) {
		return make(connective, Arrays.asList(children2));
	}

	@Override
	void toString(final StringBuilder result, final VariableNames varToName) {
		result.append("(");
		boolean isFirst = true;
		for (final Sentence child : children) {
			if (isFirst) {
				isFirst = false;
			} else {
				result.append(connective.asString);
			}
			child.toString(result, varToName);
		}
		result.append(")");
	}

	@Override
	protected Sentence alphaReduce(final Map<Variable, Variable> update) {
		return new ConnectiveSentence(connective, children.stream().map(x -> x.alphaReduce(update))
				.collect(Collectors.toList()));
	}

	@Override
	public void accept(final LogicVisitor v) {
		v.visit(this);
	}

	public Connective getConnective() {
		return connective;
	}

	public List<Sentence> getChildren() {
		return children;
	}
}
