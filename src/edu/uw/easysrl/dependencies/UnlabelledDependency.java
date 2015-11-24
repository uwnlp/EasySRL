package edu.uw.easysrl.dependencies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

/**
 * Represents a dependency with a specified attachments, but an underspecified label. The attachment can be to multiple
 * words (i.e. it's a hyperedge), so that when we do choose a label, we can make sure all the dependents have the same
 * label.
 */
public class UnlabelledDependency extends Dependency {

	/**
	 *
	 */
	private static final long serialVersionUID = -4542075352424440534L;

	public UnlabelledDependency(final int head, final Category category, final int argNumber,
			final List<Integer> argument, final Preposition preposition) {
		super(head, argNumber, category, preposition);
		this.argument = argument;

	}

	private final List<Integer> argument;

	public List<Integer> getArguments() {
		return argument;
	}

	@Override
	public boolean isResolved() {
		return true;
	}

	@Override
	public String toString() {
		return super.getHead() + "." + super.getArgNumber() + " = " + argument;
	}

	@Override
	public int hashCode() {
		return Objects.hash(argument, super.hashCode());

	}

	@Override
	public boolean equals(final Object obj) {
		final UnlabelledDependency other = (UnlabelledDependency) obj;
		return argument.equals(other.argument) && super.equals(other);
	}

	public Collection<ResolvedDependency> setLabel(final SRLLabel label) {

		List<ResolvedDependency> result;
		// Handle the inconsistent annotation of
		// "I cooked and ate fish and chips"
		if (label.isCoreArgument() || label == SRLFrame.NONE) {
			result = Arrays.asList(new ResolvedDependency(getHead(), super.getCategory(), getArgNumber(), argument.get(0),
					label, super.getPreposition()));
		} else {
			result = new ArrayList<>(argument.size());
			for (final int arg : argument) {
				result.add(new ResolvedDependency(getHead(), super.getCategory(), getArgNumber(), arg, label,
						super.getPreposition()));
			}
		}

		return result;
	}

	public int getFirstArgumentIndex() {
		return argument.get(0);
	}

	@Override
	public int getOffset() {
		return getFirstArgumentIndex() - getHead();
	}

}