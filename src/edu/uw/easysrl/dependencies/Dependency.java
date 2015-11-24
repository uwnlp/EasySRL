package edu.uw.easysrl.dependencies;

import java.io.Serializable;
import java.util.Objects;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

abstract class Dependency implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = 7957607842919931955L;
	private final int head;
	private final int argNumber;
	private final Category category;
	private final Preposition preposition;

	public Preposition getPreposition() {
		return preposition;
	}

	public Category getCategory() {
		return category;
	}

	protected Dependency(final int head, final int argNumber, final Category category, final Preposition preposition) {
		this.head = head;
		this.argNumber = argNumber;
		this.category = category;

		if (category.getArgument(argNumber) == Category.PP) {
			this.preposition = preposition;
		} else {
			this.preposition = Preposition.NONE;
		}
	}

	public abstract boolean isResolved();

	@Override
	public int hashCode() {
		return Objects.hash(argNumber, head, category, preposition);
	}

	@Override
	public boolean equals(final Object obj) {
		final Dependency other = (Dependency) obj;
		return argNumber == other.argNumber && Objects.equals(head, other.head)
				&& Objects.equals(category, other.category) && Objects.equals(preposition, other.preposition);
	}

	public final int getHead() {
		return head;
	}

	public abstract int getOffset();

	public int getArgNumber() {
		return argNumber;
	}

}