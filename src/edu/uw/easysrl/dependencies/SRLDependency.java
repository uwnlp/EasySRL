package edu.uw.easysrl.dependencies;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSortedSet;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;

public class SRLDependency implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public SRLDependency(final String predicate, final int predicateIndex,
			final List<Integer> argumentIndices, final SRLLabel label,
			final String preposition) {
		super();
		this.predicate = predicate;
		this.predicateIndex = predicateIndex;
		this.argumentIndices = ImmutableSortedSet.copyOf(argumentIndices);
		firstArgumentPosition = argumentIndices.size() == 0 ? null
				: argumentIndices.get(0);
		lastArgumentPosition = argumentIndices.size() == 0 ? null
				: argumentIndices.get(argumentIndices.size() - 1);

		final List<Integer> firstConstituent = new ArrayList<>();
		for (int i = firstArgumentPosition; i <= lastArgumentPosition
				&& argumentIndices.contains(i); i++) {
			firstConstituent.add(i);
		}
		this.firstConstituent = ImmutableSortedSet.copyOf(firstConstituent);

		this.preposition = preposition;
		this.label = label;
	}

	private final String predicate;
	private final int predicateIndex;
	private final String preposition;

	private final Integer firstArgumentPosition;
	private final Integer lastArgumentPosition;

	public String getPredicate() {
		return predicate;
	}

	public String getPreposition() {
		return preposition;
	}

	public int getPredicateIndex() {
		return predicateIndex;
	}

	public Collection<Integer> getArgumentPositions() {
		return argumentIndices;
	}

	public Collection<Integer> getFirstArgumentConstituent() {
		return firstConstituent;
	}

	public SRLLabel getLabel() {
		return label;
	}

	private final Set<Integer> argumentIndices;
	private final Set<Integer> firstConstituent;
	private final SRLLabel label;

	public boolean isCoreArgument() {
		return label.isCoreArgument();
	}

	public String toString(final List<String> words) {
		final StringBuilder result = new StringBuilder();
		result.append(words.get(predicateIndex));
		result.append(" " + label);
		for (final int i : argumentIndices) {
			result.append(" " + words.get(i));
		}

		return result.toString();
	}

	@Override
	public String toString() {
		return predicate + ":" + label + "-->" + argumentIndices;
	}

	public Integer getLastArgumentPosition() {
		return lastArgumentPosition;
	}

	public Integer getFirstArgumentPosition() {
		return firstArgumentPosition;
	}

}
