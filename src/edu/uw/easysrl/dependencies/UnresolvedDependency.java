package edu.uw.easysrl.dependencies;

import java.util.List;
import java.util.Objects;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

class UnresolvedDependency extends Dependency {

	/**
	 *
	 */
	private static final long serialVersionUID = -9006670237313656096L;
	final int argumentID;

	UnresolvedDependency(final int head, final Category category, final int argNumber, final int argumentID,
			final Preposition preposition) {
		super(head, argNumber, category, preposition);
		this.argumentID = argumentID;
	}

	Dependency resolve(final int id) {

		return new UnresolvedDependency(super.getHead(), super.getCategory(), super.getArgNumber(), id, super.getPreposition());
	}

	Dependency resolve(final List<Integer> argument) {
		return new UnlabelledDependency(super.getHead(), super.getCategory(), super.getArgNumber(), argument, super.getPreposition());
	}

	UnresolvedDependency setPreposition(final Preposition preposition) {
		if (getPreposition() == preposition) {
			return this;
		}

		return new UnresolvedDependency(super.getHead(), super.getCategory(), super.getArgNumber(), argumentID, preposition);
	}

	@Override
	public String toString() {
		return super.getHead() + "." + super.getArgNumber() + " = " + argumentID
				+ (super.getPreposition() == Preposition.NONE ? "" : " " + super.getPreposition());
	}

	@Override
	public boolean isResolved() {
		return false;
	}

	private int hashCode = 0;

	@Override
	public int hashCode() {
		if (hashCode == 0) {
			hashCode = Objects.hash(this.argumentID, super.hashCode());
		}

		return hashCode;
	}

	@Override
	public boolean equals(final Object obj) {
		final UnresolvedDependency other = (UnresolvedDependency) obj;
		return argumentID == other.argumentID && super.equals(obj);
	}

	@Override
	public int getOffset() {
		throw new UnsupportedOperationException();
	}

}