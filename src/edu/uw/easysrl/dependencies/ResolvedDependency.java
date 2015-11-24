package edu.uw.easysrl.dependencies;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

public class ResolvedDependency extends Dependency {

	/**
	 *
	 */
	private static final long serialVersionUID = -4542075352424440534L;
	private final SRLLabel semanticRole;

	public ResolvedDependency(final int head, final Category category, final int argNumber, final int argument,
			final SRLLabel semanticRole, final Preposition preposition) {
		super(head, argNumber, category, preposition);
		this.argument = argument;
		this.semanticRole = semanticRole;
	}

	private final int argument;

	@Override
	public boolean isResolved() {
		return true;
	}

	@Override
	public String toString() {
		return super.getHead() + "." + super.getArgNumber() + (getSemanticRole() != null ? " " + getSemanticRole() : "") + " = "
				+ argument;
	}

	@Override
	public int hashCode() {
		return Objects.hash(argument, semanticRole, super.hashCode());
	}

	public SRLLabel getSemanticRole() {
		return semanticRole;
	}

	@Override
	public boolean equals(final Object obj) {
		final ResolvedDependency other = (ResolvedDependency) obj;
		return argument == other.argument && this.semanticRole == other.semanticRole && super.equals(other);
	}

	public ResolvedDependency overwriteLabel(final SRLLabel label) {
		return new ResolvedDependency(getHead(), super.getCategory(), getArgNumber(), argument, label, super.getPreposition());
	}

	public int getArgumentIndex() {
		return argument;
	}

	@Override
	public int getOffset() {
		return argument - getHead();
	}

	public int getPropbankPredicateIndex() {
		if (semanticRole.isCoreArgument()) {
			return getHead();
		} else {
			return argument;
		}
	}

	public int getPropbankArgumentIndex() {
		if (semanticRole.isCoreArgument()) {
			return argument;
		} else {
			return getHead();
		}
	}

	public UnlabelledDependency dropLabel() {
		return new UnlabelledDependency(getHead(), super.getCategory(), getArgNumber(), Arrays.asList(argument), getPreposition());
	}

	public int getArgument() {
		return argument;
	}

	public String toString(final List<String> words) {
		return words.get(super.getHead()) + "." + super.getArgNumber()
				+ (getSemanticRole() != null ? " " + getSemanticRole() : "") + " = " + words.get(argument);
	}

}