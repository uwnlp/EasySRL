package edu.uw.easysrl.dependencies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import edu.uw.easysrl.syntax.grammar.Preposition;

/**
 * Used to unify two dependency structures
 *
 */
class UnifyingSubstitution {
	private final Map<Integer, Coindexation.IDorHead> substitutions;
	private final Map<Integer, Preposition> prepositionSubstitutions;
	private final Map<List<Integer>, Coindexation.IDorHead> headSubstitutions;

	private UnifyingSubstitution(final Map<Integer, Coindexation.IDorHead> substitutions,
			final Map<Integer, Preposition> prepositionSubstitutions,
			final Map<List<Integer>, Coindexation.IDorHead> headSubstitutions) {
		super();
		this.substitutions = substitutions;
		this.prepositionSubstitutions = prepositionSubstitutions;
		this.headSubstitutions = headSubstitutions;
	}

	static UnifyingSubstitution make(final Coindexation left, final Coindexation right, final boolean isConjunction) {
		final Map<Integer, Coindexation.IDorHead> substitutions = new HashMap<>();
		final Map<Integer, Preposition> prepositionSubstitutions = new HashMap<>();
		final Map<List<Integer>, Coindexation.IDorHead> headSubstitutions = new HashMap<>();

		make(left, right, substitutions, prepositionSubstitutions, headSubstitutions, new AtomicInteger(0),
				isConjunction);
		return new UnifyingSubstitution(substitutions, prepositionSubstitutions, headSubstitutions);
	}

	private static void make(final Coindexation left, final Coindexation right,
			final Map<Integer, Coindexation.IDorHead> substitutions,
			final Map<Integer, Preposition> prepositionSubstitutions,
			final Map<List<Integer>, Coindexation.IDorHead> headSubstitutions, final AtomicInteger freshID,
			final boolean isConjunction) {
		final Preposition newPreposition;

		if (left.preposition == Preposition.UNSPECIFIED) {
			newPreposition = right.preposition;
		} else if (right.preposition == Preposition.UNSPECIFIED) {
			newPreposition = left.preposition;
		} else {
			newPreposition = null;
		}

		if (!right.idOrHead.isHead()) {
			if (!left.idOrHead.isHead()) {
				// See if we already have an entry for either ID.
				Coindexation.IDorHead id = substitutions.getOrDefault(left.idOrHead.id,
						substitutions.get(right.idOrHead.id));

				if (id == null) {
					// No entry for either ID. Make a fresh ID.
					id = new Coindexation.IDorHead(freshID.decrementAndGet());
				}

				// Make both unify to the same ID.
				substitutions.put(left.idOrHead.id, id);
				substitutions.put(right.idOrHead.id, id);
				prepositionSubstitutions.put(left.idOrHead.id, newPreposition);
			} else {
				// Update the right ID to point to the left Head
				substitutions.put(right.idOrHead.id, left.idOrHead);
			}
			prepositionSubstitutions.put(right.idOrHead.id, newPreposition);
		} else {
			if (isConjunction) {
				// Allow coordinated phrases to have multiple heads.
				final List<Integer> coordinatedHead = new ArrayList<>();
				coordinatedHead.addAll(right.idOrHead.head);
				if (left.idOrHead.head != null) {
					coordinatedHead.addAll(left.idOrHead.head);
					headSubstitutions.put(left.idOrHead.head, new Coindexation.IDorHead(coordinatedHead));
				}
				headSubstitutions.put(right.idOrHead.head, new Coindexation.IDorHead(coordinatedHead));
			} else {
				// Update the left ID to point to the right Head
				substitutions.put(left.idOrHead.id, right.idOrHead);
			}

			prepositionSubstitutions.put(left.idOrHead.id, newPreposition);
		}

		// Recurse on the children.
		if (left.left != null) {
			make(left.left, right.left, substitutions, prepositionSubstitutions, headSubstitutions, freshID,
					isConjunction);
		}
		if (left.right != null) {
			make(left.right, right.right, substitutions, prepositionSubstitutions, headSubstitutions, freshID,
					isConjunction);
		}
	}

	Dependency applyTo(UnresolvedDependency dep) {
		final Preposition prep = prepositionSubstitutions.get(dep.argumentID);
		if (prep != null) {
			dep = dep.setPreposition(prep);
		}

		final Coindexation.IDorHead result = substitutions.get(dep.argumentID);
		if (result == null) {
			return dep;
		} else if (result.isHead()) {
			return dep.resolve(result.head);
		} else {
			return dep.resolve(result.id);
		}
	}

	Coindexation applyTo(final Coindexation coindexation) {
		if (coindexation == null) {
			return null;
		}
		Coindexation.IDorHead newID = coindexation.idOrHead.isHead() ? headSubstitutions
				.get(coindexation.idOrHead.head) : substitutions.get(coindexation.idOrHead.id);

				if (newID == null) {
					newID = coindexation.idOrHead;
				}
				if (coindexation.left == null && coindexation.right == null) {
					final Preposition newPrep = prepositionSubstitutions.get(coindexation.idOrHead.id);
					return new Coindexation(newID, newPrep != null ? newPrep : coindexation.preposition);
				} else {
					return new Coindexation(applyTo(coindexation.left), applyTo(coindexation.right), newID);
				}
	}
}