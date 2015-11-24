package edu.uw.easysrl.dependencies;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.carrotsearch.hppc.IntIntHashMap;
import com.google.common.base.Preconditions;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.util.Util;

/**
 *
 *
 */
public class DependencyStructure implements Serializable {

	private static final long serialVersionUID = -2075193454239570840L;

	/**
	 * Dependencies with underspecified attachments
	 */
	private final Set<UnresolvedDependency> unresolvedDependencies;
	private final Coindexation coindexation;
	private final boolean isConjunction;

	private DependencyStructure(final Coindexation coindexation,
			final Set<UnresolvedDependency> unresolvedDependencies, final boolean isConjunction) {

		this.coindexation = coindexation;
		this.unresolvedDependencies = unresolvedDependencies;
		this.isConjunction = isConjunction;
	}

	/**
	 * Returns the head of this DependencyStructure. Coordination can lead to multiple heads, in which case this method
	 * returns an arbitrary head.
	 */
	public int getArbitraryHead() {
		return coindexation.idOrHead.head.get(0);
	}

	private int hashcode = 0;

	@Override
	public int hashCode() {
		if (hashcode == 0) {
			hashcode = Objects.hash(unresolvedDependencies, coindexation, isConjunction);
		}
		return hashcode;
	}

	@Override
	public boolean equals(final Object obj) {
		final DependencyStructure other = (DependencyStructure) obj;
		return coindexation.equals(other.coindexation) && unresolvedDependencies.equals(other.unresolvedDependencies)
				&& isConjunction == other.isConjunction;
	}

	@Override
	public String toString() {
		return getArbitraryHead() + " " + unresolvedDependencies + " " + coindexation;
	}

	public Coindexation getCoindexation() {
		return coindexation;
	}

	public static DependencyStructure make(final Category category, final String word, final int sentencePosition) {
		Coindexation coindexation = Coindexation.fromString(category, sentencePosition);
		if (Preposition.isPrepositionCategory(category)) {
			coindexation = coindexation.specifyPreposition(Preposition.fromString(word));
		}
		return new DependencyStructure(coindexation, createUnresolvedDependencies(coindexation, category), false);
	}

	public static DependencyStructure makeUnaryRuleTransformation(final String from, final String to) {

		// N_1\N_1 S\NP_1 ---> (N_1\N_1)/(S\NP_1)
		final String cat = maybeBracket(to) + "/" + maybeBracket(from);
		return new DependencyStructure(Coindexation.fromString(cat, new Coindexation.IDorHead(-1), new HashMap<>(), -1,
				true), Collections.emptySet());
	}

	private static String maybeBracket(final String input) {
		return Util.findNonNestedChar(input, "\\/") == -1 ? input : "(" + input + ")";
	}

	private DependencyStructure(final Coindexation coindexation, final Set<UnresolvedDependency> unresolvedDependencies) {
		this(coindexation, unresolvedDependencies, false);
	}

	/**
	 * Applies this dependency structure to another, adding any new dependencies to @newResolvedDependencies
	 */
	public DependencyStructure apply(DependencyStructure other, final List<UnlabelledDependency> newResolvedDependencies) {
		other = other.standardizeApart(coindexation.getMaxID() + 1);

		final UnifyingSubstitution substitution = UnifyingSubstitution.make(coindexation.right, other.coindexation,
				isConjunction);

		final Set<UnresolvedDependency> newUnresolvedDependencies = new HashSet<>();
		final Coindexation newCoindexation = substitution.applyTo(coindexation.left);
		updateResolvedDependencies(other, substitution, newUnresolvedDependencies, newResolvedDependencies);

		final Set<UnresolvedDependency> normalizedUnresolvedDependencies = new HashSet<>(
				newUnresolvedDependencies.size());

		final Coindexation normalizedCoindexation = normalize(newCoindexation, newUnresolvedDependencies,
				normalizedUnresolvedDependencies, newResolvedDependencies, 1);
		return new DependencyStructure(normalizedCoindexation, normalizedUnresolvedDependencies);
	}

	public DependencyStructure conjunction() {
		return new DependencyStructure(new Coindexation(coindexation, coindexation, coindexation.idOrHead),
				unresolvedDependencies, true);
	}

	/**
	 * Applies this dependency structure with another, adding any new dependencies to @newResolvedDependencies
	 */
	public DependencyStructure compose(DependencyStructure other,
			final List<UnlabelledDependency> newResolvedDependencies) {

		other = other.standardizeApart(coindexation.getMaxID() + 1);
		final UnifyingSubstitution substitution = UnifyingSubstitution.make(coindexation.right,
				other.coindexation.left, false);

		final Set<UnresolvedDependency> newUnresolvedDependencies = new HashSet<>();
		updateResolvedDependencies(other, substitution, newUnresolvedDependencies, newResolvedDependencies);

		final Coindexation newCoindexationLeft = substitution.applyTo(coindexation.left);
		final Coindexation newCoindexationRight = substitution.applyTo(other.coindexation.right);
		final boolean headIsLeft = !coindexation.left.idOrHead.equals(coindexation.right.idOrHead);
		final Coindexation.IDorHead idOrHead = substitution.applyTo(headIsLeft ? coindexation : other.coindexation).idOrHead;

		final Set<UnresolvedDependency> normalizedUnresolvedDependencies = new HashSet<>(
				newUnresolvedDependencies.size());
		final Coindexation normalizedCoindexation = normalize(new Coindexation(newCoindexationLeft,
				newCoindexationRight, idOrHead), newUnresolvedDependencies, normalizedUnresolvedDependencies,
				newResolvedDependencies, 1);

		return new DependencyStructure(normalizedCoindexation, normalizedUnresolvedDependencies);
	}

	/**
	 * Generalized forward composition (to degree 2)
	 */
	public DependencyStructure compose2(DependencyStructure other,
			final List<UnlabelledDependency> newResolvedDependencies) {
		// A/B (B/C)/D ---> (A/C)/D
		other = other.standardizeApart(coindexation.getMaxID() + 1);

		final UnifyingSubstitution substitution = UnifyingSubstitution.make(coindexation.right,
				other.coindexation.left.left, false);

		final Set<UnresolvedDependency> newUnresolvedDependencies = new HashSet<>();

		updateResolvedDependencies(other, substitution, newUnresolvedDependencies, newResolvedDependencies);
		final Set<UnresolvedDependency> normalizedUnresolvedDependencies = new HashSet<>(
				newUnresolvedDependencies.size());
		final Coindexation normalizedCoindexation;
		if (coindexation.isModifier()) {
			// X/X X/Y/Z
			normalizedCoindexation = normalize(substitution.applyTo(other.coindexation), newUnresolvedDependencies,
					normalizedUnresolvedDependencies, newResolvedDependencies, 1);
		} else {
			// S\NP/NP NP/PP/PP
			final Coindexation leftWithSubstitution = substitution.applyTo(coindexation);
			final Coindexation rightWithSubstitution = substitution.applyTo(other.coindexation);
			normalizedCoindexation = normalize(new Coindexation(new Coindexation(leftWithSubstitution.left,
					rightWithSubstitution.left.right, leftWithSubstitution.idOrHead), rightWithSubstitution.right,
					leftWithSubstitution.idOrHead), newUnresolvedDependencies, normalizedUnresolvedDependencies,
					newResolvedDependencies, 1);
		}

		return new DependencyStructure(normalizedCoindexation, normalizedUnresolvedDependencies);
	}

	/**
	 * Returns an equivalent dependency structure, where all the IDs are >=@minID
	 */
	private DependencyStructure standardizeApart(final int minID) {
		final Set<UnresolvedDependency> normalizedUnresolvedDependencies = new HashSet<>(unresolvedDependencies.size());
		final Coindexation newArgument = normalize(coindexation, unresolvedDependencies,
				normalizedUnresolvedDependencies, Collections.emptyList(), minID);

		return new DependencyStructure(newArgument, normalizedUnresolvedDependencies);
	}

	private static void normalize(final Collection<UnresolvedDependency> unresolvedDependencies,
			final IntIntHashMap substitutions, final Set<UnresolvedDependency> newUnresolvedDependencies,
			final Collection<UnlabelledDependency> newResolvedDependencies) {
		for (final UnresolvedDependency dep : unresolvedDependencies) {
			final int newID = substitutions.getOrDefault(dep.argumentID, Integer.MIN_VALUE);
			if (newID != Integer.MIN_VALUE) {
				if (newID == dep.argumentID) {
					newUnresolvedDependencies.add(dep);
				} else {
					newUnresolvedDependencies.add(new UnresolvedDependency(dep.getHead(), dep.getCategory(), dep.getArgNumber(),
							newID, dep.getPreposition()));
				}
			} else {
				// Dependencies that don't get any attachment (e.g. in arbitrary control).
				newResolvedDependencies.add(new UnlabelledDependency(dep.getHead(), dep.getCategory(), dep.getArgNumber(),
						Collections.singletonList(dep.getHead()), dep.getPreposition()));
			}
		}
	}

	private static Coindexation normalize(final Coindexation coindexation,
			final Set<UnresolvedDependency> unresolvedDependencies,
			final Set<UnresolvedDependency> normalizedUnresolvedDependencies,
			final List<UnlabelledDependency> newResolvedDependencies, final int minValue) {
		final IntIntHashMap normalizingSubsitution = new IntIntHashMap();
		final Coindexation normalizedCoindexation = coindexation.normalize(normalizingSubsitution, minValue);
		normalize(unresolvedDependencies, normalizingSubsitution, normalizedUnresolvedDependencies,
				newResolvedDependencies);
		return normalizedCoindexation;
	}

	private static Set<UnresolvedDependency> createUnresolvedDependencies(Coindexation coindexation,
			final Category category) {
		final Set<UnresolvedDependency> unresolvedDependencies = new HashSet<>();

		for (final int head : coindexation.idOrHead.head) {
			int argNumber = coindexation.countNumberOfArguments();

			// Assign arguments with the leftmost having argument number 1.
			while (coindexation.right != null) {
				Preconditions.checkState(!coindexation.right.idOrHead.isHead());
				unresolvedDependencies.add(new UnresolvedDependency(head, category, argNumber,
						coindexation.right.idOrHead.id, coindexation.preposition));

				coindexation = coindexation.left;
				argNumber--;
			}
		}

		return unresolvedDependencies;
	}

	private static void updateDependencies(final DependencyStructure other, final UnifyingSubstitution substitution,
			final Set<UnresolvedDependency> newUnresolvedDependencies,
			final List<UnlabelledDependency> newResolvedDependencies) {
		for (final UnresolvedDependency dep : other.unresolvedDependencies) {
			final Dependency updated = substitution.applyTo(dep);

			if (updated.isResolved()) {
				newResolvedDependencies.add((UnlabelledDependency) updated);
			} else {
				newUnresolvedDependencies.add((UnresolvedDependency) updated);
			}
		}
	}

	private void updateResolvedDependencies(final DependencyStructure other, final UnifyingSubstitution substitution,
			final Set<UnresolvedDependency> newUnresolvedDependencies,
			final List<UnlabelledDependency> newResolvedDependencies) {

		updateDependencies(this, substitution, newUnresolvedDependencies, newResolvedDependencies);
		updateDependencies(other, substitution, newUnresolvedDependencies, newResolvedDependencies);
	}
}