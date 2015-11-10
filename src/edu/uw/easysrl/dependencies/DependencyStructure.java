package edu.uw.easysrl.dependencies;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.carrotsearch.hppc.IntIntHashMap;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.util.Util;

/**
 *
 *
 */
public class DependencyStructure implements Serializable {

	private static final long serialVersionUID = -2075193454239570840L;

	private final Set<UnresolvedDependency> unresolvedDependencies;

	private static final Map<Category, String> categoryToMarkedUpCategory = new HashMap<>();

	/**
	 * Loads co-indexation information from the specified file.
	 */
	public static void parseMarkedUpFile(final File file) throws IOException {
		final Iterator<String> lines = Util.readFileLineByLine(file);
		while (lines.hasNext()) {
			String line = lines.next();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			line = line.replaceAll(":B", "");

			categoryToMarkedUpCategory.put(Category.valueOf(line), line);
		}
	}

	public static abstract class Dependency implements Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 7957607842919931955L;
		final int head;
		final int argNumber;
		final SRLLabel semanticRole;
		private final Category category;
		protected final Preposition preposition;

		public Preposition getPreposition() {
			return preposition;
		}

		public Category getCategory() {
			return category;
		}

		public int getHead() {
			return head;
		}

		public int getArgNumber() {
			return argNumber;
		}

		public SRLLabel getSemanticRole() {
			return semanticRole;
		}

		private Dependency(final int head, final int argNumber, final Category category, final SRLLabel semanticRole,
				final Preposition preposition) {
			this.head = head;
			this.argNumber = argNumber;
			this.semanticRole = semanticRole;
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
			return Objects.hash(argNumber, head, semanticRole, category, preposition);

		}

		@Override
		public boolean equals(final Object obj) {
			final Dependency other = (Dependency) obj;
			return argNumber == other.argNumber && Objects.equals(head, other.head)
					&& Objects.equals(semanticRole, other.semanticRole) && Objects.equals(category, other.category)
					&& Objects.equals(preposition, other.preposition);
		}

		/**
		 * True if the argument has no SRL label, but SHOULD.
		 */
		public boolean isUnlabelled() {
			return semanticRole == SRLFrame.UNLABELLED_ARGUMENT;
		}

		public final int getPredicateIndex() {
			return head;
		}

		public Collection<ResolvedDependency> setLabel(@SuppressWarnings("unused") final SRLLabel label) {
			throw new UnsupportedOperationException();
		}

		public abstract int getOffset();

	}

	public static class UnresolvedDependency extends Dependency {

		/**
		 *
		 */
		private static final long serialVersionUID = -9006670237313656096L;
		private final int argumentID;

		private UnresolvedDependency(final int head, final Category category, final int argNumber,
				final int argumentID, final SRLLabel semanticRole, final Preposition preposition) {
			super(head, argNumber, category, semanticRole, preposition);
			this.argumentID = argumentID;
		}

		private Dependency resolve(final int id) {

			return new UnresolvedDependency(super.head, super.category, super.argNumber, id, super.semanticRole,
					super.preposition);
		}

		private Dependency resolve(final List<Integer> argument) {
			return new UnlabelledDependency(super.head, super.category, super.argNumber, argument, super.semanticRole,
					super.preposition);
		}

		private UnresolvedDependency setPreposition(final Preposition preposition) {
			if (super.preposition == preposition) {
				return this;
			}

			return new UnresolvedDependency(super.head, super.category, super.argNumber, argumentID,
					super.semanticRole, preposition);
		}

		@Override
		public String toString() {
			return super.head + "." + super.argNumber + " = " + argumentID
					+ (super.preposition == Preposition.NONE ? "" : " " + super.preposition);
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

	public static class UnlabelledDependency extends Dependency {

		/**
		 *
		 */
		private static final long serialVersionUID = -4542075352424440534L;

		public UnlabelledDependency(final int head, final Category category, final int argNumber,
				final List<Integer> argument, final SRLLabel semanticRole, final Preposition preposition) {
			super(head, argNumber, category, semanticRole, preposition);
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
			return super.head + "." + super.argNumber + (getSemanticRole() != null ? " " + getSemanticRole() : "")
					+ " = " + argument;
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

		@Override
		public Collection<ResolvedDependency> setLabel(final SRLLabel label) {
			if (getSemanticRole() != SRLFrame.UNLABELLED_ARGUMENT) {
				throw new RuntimeException("Trying to label already non-SRL dependency");
			}

			List<ResolvedDependency> result;
			// Handle the inconsistent annotation of
			// "I cooked and ate fish and chips"
			if (label.isCoreArgument() || label == SRLFrame.NONE) {
				result = Arrays.asList(new ResolvedDependency(getHead(), super.category, getArgNumber(), argument
						.get(0), label, super.preposition));
			} else {
				result = new ArrayList<>(argument.size());
				for (final int arg : argument) {
					result.add(new ResolvedDependency(getHead(), super.category, getArgNumber(), arg, label,
							super.preposition));
				}
			}

			return result;
		}

		public int getArgumentIndex() {
			return argument.get(0);
		}

		@Override
		public int getOffset() {
			return getArgumentIndex() - getPredicateIndex();
		}

	}

	public static class ResolvedDependency extends Dependency {

		/**
		 *
		 */
		private static final long serialVersionUID = -4542075352424440534L;

		public ResolvedDependency(final int head, final Category category, final int argNumber, final int argument,
				final SRLLabel semanticRole, final Preposition preposition) {
			super(head, argNumber, category, semanticRole, preposition);
			this.argument = argument;

		}

		private final int argument;

		@Override
		public boolean isResolved() {
			return true;
		}

		@Override
		public String toString() {
			return super.head + "." + super.argNumber + (getSemanticRole() != null ? " " + getSemanticRole() : "")
					+ " = " + argument;
		}

		@Override
		public int hashCode() {
			return Objects.hash(argument, super.hashCode());
		}

		@Override
		public boolean equals(final Object obj) {
			final ResolvedDependency other = (ResolvedDependency) obj;
			return argument == other.argument && super.equals(other);
		}

		public ResolvedDependency overwriteLabel(final SRLLabel label) {
			return new ResolvedDependency(getHead(), super.category, getArgNumber(), argument, label, super.preposition);
		}

		public int getArgumentIndex() {
			return argument;
		}

		public Object toStringUnlabelled() {
			return super.head + "." + super.argNumber + " = " + argument;

		}

		@Override
		public int getOffset() {
			return argument - head;
		}

		public int getPropbankPredicateIndex() {
			if (semanticRole.isCoreArgument()) {
				return head;
			} else {
				return argument;
			}
		}

		public int getPropbankArgumentIndex() {
			if (semanticRole.isCoreArgument()) {
				return argument;
			} else {
				return head;
			}
		}

		public UnlabelledDependency dropLabel() {
			return new UnlabelledDependency(head, super.category, argNumber, Arrays.asList(argument), semanticRole,
					preposition);
		}

		public int getArgument() {
			return argument;
		}

		public String toString(final List<String> words) {
			return words.get(super.head) + "." + super.argNumber
					+ (getSemanticRole() != null ? " " + getSemanticRole() : "") + " = " + words.get(argument);
		}

	}

	static class ID {

	}

	/**
	 * Represents the endpoint of a dependency. For fully-specified dependencies, this will be a 'head'. Multiple heads
	 * are possible to deal with coordination. Otherwise, it will be an 'id', referring to some coindexation.
	 */
	public static class IDorHead implements Serializable {

		private static final long serialVersionUID = -2338555345725737539L;
		private final List<Integer> head;
		final Integer id;

		public boolean isHead() {
			return head != null;
		}

		public IDorHead(final List<Integer> head) {
			if (head != null && head.indexOf(-1) > -1) {
				Util.debugHook();
			}

			this.head = head;
			this.id = null;
		}

		public IDorHead(final Integer id) {
			this.head = null;
			this.id = id;
		}

		@Override
		public String toString() {
			if (head == null) {
				return "" + id;
			} else {
				return head.toString();
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(head, id);
		}

		@Override
		public boolean equals(final Object obj) {
			final IDorHead other = (IDorHead) obj;
			return Objects.equals(head, other.head) && Objects.equals(id, other.id);
		}

		public int getID() {
			return id;
		}

		public List<Integer> getHeadIndices() {
			return head;
		}

	}

	private final Coindexation coindexation;

	private final boolean isConjunction;

	private DependencyStructure(final Coindexation coindexation,
			final Set<UnresolvedDependency> unresolvedDependencies, final boolean isConjunction) {

		this.coindexation = coindexation;
		this.unresolvedDependencies = unresolvedDependencies;
		this.isConjunction = isConjunction;
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
					newUnresolvedDependencies.add(new UnresolvedDependency(dep.head, dep.getCategory(), dep.argNumber,
							newID, dep.semanticRole, dep.getPreposition()));
				}
			} else {
				// Dependencies that end up not getting resolved
				// This just removes them (which may be the right thing to do).
				newResolvedDependencies.add(new UnlabelledDependency(dep.head, dep.getCategory(), dep.argNumber, Arrays
						.asList(dep.head), dep.semanticRole, dep.getPreposition()));
			}
		}

	}

	public int getArbitraryHead() {
		return coindexation.idOrHead.head.get(0);
	}

	public boolean hasNoArguments() {
		return coindexation.countNumberOfArguments() == 0;
	}

	public Set<UnresolvedDependency> getUnresolvedDependencies() {
		return unresolvedDependencies;
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

	public static DependencyStructure fromString(final Category category, final String markedUpCategory,
			final Integer sentencePosition) {
		// gave ((S\NP_1)/((NP_3/PP_1)_4/PP_2))/NP_2
		// clever N_i/N_i

		final int wordIndex = sentencePosition;

		final Coindexation coindexation = Coindexation.fromString(markedUpCategory,
				new IDorHead(Arrays.asList(wordIndex)), new HashMap<>(), wordIndex, true);

		return new DependencyStructure(coindexation, category);
	}

	public static DependencyStructure make(final Category category, final String word, final int sentencePosition) {
		String markedup;

		markedup = categoryToMarkedUpCategory.get(category);
		if (markedup == null) {
			return null;
		}

		DependencyStructure result = fromString(category, markedup, sentencePosition);
		if (Preposition.isPrepositionCategory(category)) {
			result = result.specifyPreposition(Preposition.fromString(word));
		}
		return result;
	}

	public static String getMarkedUp(final Category category) {
		return categoryToMarkedUpCategory.get(category);
	}

	public static DependencyStructure makeUnaryRuleTransformation(final String from, final String to) {

		// N_1\N_1 S\NP_1 ---> (N_1\N_1)/(S\NP_1)
		final String cat = maybeBracket(to) + "/" + maybeBracket(from);
		return new DependencyStructure(Coindexation.fromString(cat, new IDorHead(-1), new HashMap<>(), -1, true),
				Collections.emptySet());
	}

	private static String maybeBracket(final String input) {
		return Util.findNonNestedChar(input, "\\/") == -1 ? input : "(" + input + ")";
	}

	private static Set<UnresolvedDependency> createUnresolvedDependencies(Coindexation argument, final Category category) {
		final Set<UnresolvedDependency> unresolvedDependencies = new HashSet<>();

		for (final int head : argument.idOrHead.head) {

			if (head == -1) {
				// Used to stop unary rules creating dependencies to their
				// arguments.
				continue;
			}

			int argNumber = 0;

			// First, count how many arguments there are.
			Coindexation forCounting = argument;
			while (forCounting.right != null) {
				forCounting = forCounting.left;
				argNumber++;
			}

			// Assign arguments with the leftmost having argument number 1.
			while (argument.right != null) {
				if (!argument.right.idOrHead.isHead()) {
					final SRLLabel semanticRole = SRLFrame.UNLABELLED_ARGUMENT;
					unresolvedDependencies.add(new UnresolvedDependency(head, category, argNumber,
							argument.right.idOrHead.id, semanticRole, argument.preposition));
				}
				argument = argument.left;
				argNumber--;
			}
		}

		return unresolvedDependencies;
	}

	private static void updateDependencies(final DependencyStructure other, final UnifyingSubstitution substitution,
			final Set<UnresolvedDependency> newUnresolvedDependencies,
			final List<UnlabelledDependency> newResolvedDependencies, final boolean isLeft) {
		for (final UnresolvedDependency dep : other.getUnresolvedDependencies()) {
			final Dependency updated = substitution.applyTo(dep, isLeft);

			if (updated.isResolved()) {
				newResolvedDependencies.add((UnlabelledDependency) updated);
			} else {
				final UnresolvedDependency unresolved = (UnresolvedDependency) updated;
				newUnresolvedDependencies.add(unresolved);
			}
		}
	}

	public DependencyStructure(final Coindexation argument, final Category category) {
		this(argument, DependencyStructure.createUnresolvedDependencies(argument, category), false);
	}

	public DependencyStructure(final Coindexation argument, final Set<UnresolvedDependency> unresolvedDependencies) {
		this(argument, unresolvedDependencies, false);
	}

	public DependencyStructure apply(DependencyStructure other, final List<UnlabelledDependency> newResolvedDependencies) {
		other = standardizeApart(other, coindexation.getMaxID() + 1);

		final UnifyingSubstitution substitution = UnifyingSubstitution.make(coindexation.right, other.coindexation,
				isConjunction);

		final Set<UnresolvedDependency> newUnresolvedDependencies = new HashSet<>();

		final Coindexation newArgument = substitution.applyTo(coindexation.left, true);

		updateResolvedDependencies(other, substitution, newUnresolvedDependencies, newResolvedDependencies);

		final Set<UnresolvedDependency> normalizedUnresolvedDependencies = new HashSet<>(
				newUnresolvedDependencies.size());

		final Coindexation normalizedArgument = normalize(newArgument, newUnresolvedDependencies,
				normalizedUnresolvedDependencies, newResolvedDependencies, 1);
		return new DependencyStructure(normalizedArgument, normalizedUnresolvedDependencies);

	}

	private static Coindexation normalize(final Coindexation argument,
			final Set<UnresolvedDependency> unresolvedDependencies,
			final Set<UnresolvedDependency> normalizedUnresolvedDependencies,
			final List<UnlabelledDependency> newResolvedDependencies, final int minValue) {
		final IntIntHashMap normalizingSubsitution = new IntIntHashMap();
		final Coindexation normalizedArgument = argument.normalize(normalizingSubsitution, minValue);
		normalize(unresolvedDependencies, normalizingSubsitution, normalizedUnresolvedDependencies,
				newResolvedDependencies);
		return normalizedArgument;

		// return argument;
	}

	private void updateResolvedDependencies(final DependencyStructure other, final UnifyingSubstitution substitution,
			final Set<UnresolvedDependency> newUnresolvedDependencies,
			final List<UnlabelledDependency> newResolvedDependencies) {

		updateDependencies(this, substitution, newUnresolvedDependencies, newResolvedDependencies, true);
		updateDependencies(other, substitution, newUnresolvedDependencies, newResolvedDependencies, false);
	}

	public DependencyStructure conjunction() {
		return new DependencyStructure(new Coindexation(coindexation, coindexation, coindexation.idOrHead),
				getUnresolvedDependencies(), true);
	}

	private static DependencyStructure standardizeApart(final DependencyStructure other, final int minID) {
		final Set<UnresolvedDependency> normalizedUnresolvedDependencies = new HashSet<>(
				other.unresolvedDependencies.size());
		final Coindexation newArgument = normalize(other.coindexation, other.unresolvedDependencies,
				normalizedUnresolvedDependencies, Collections.emptyList(), minID);

		return new DependencyStructure(newArgument, normalizedUnresolvedDependencies);
	}

	public DependencyStructure compose(DependencyStructure other,
			final List<UnlabelledDependency> newResolvedDependencies) {

		other = standardizeApart(other, coindexation.getMaxID() + 1);
		final UnifyingSubstitution substitution = UnifyingSubstitution.make(coindexation.right,
				other.coindexation.left, false);

		final Set<UnresolvedDependency> newUnresolvedDependencies = new HashSet<>();
		updateResolvedDependencies(other, substitution, newUnresolvedDependencies, newResolvedDependencies);

		final Coindexation newArgumentLeft = substitution.applyTo(coindexation.left, true);
		final Coindexation newArgumentRight = substitution.applyTo(other.coindexation.right, false);
		final boolean headIsLeft = !coindexation.left.idOrHead.equals(coindexation.right.idOrHead);
		final IDorHead idOrHead = substitution.applyTo(headIsLeft ? coindexation : other.coindexation, headIsLeft).idOrHead;

		final Set<UnresolvedDependency> normalizedUnresolvedDependencies = new HashSet<>(
				newUnresolvedDependencies.size());
		final Coindexation normalizedArgument = normalize(
				new Coindexation(newArgumentLeft, newArgumentRight, idOrHead), newUnresolvedDependencies,
				normalizedUnresolvedDependencies, newResolvedDependencies, 1);

		return new DependencyStructure(normalizedArgument, normalizedUnresolvedDependencies);
	}

	public static class UnifyingSubstitution {
		private final Map<Integer, IDorHead> leftSubstitutions;
		private final Map<Integer, IDorHead> rightSubstitutions;

		private final Map<Integer, Preposition> leftPrepositionSubstitutions;
		private final Map<Integer, Preposition> rightPrepositionSubstitutions;

		private final Map<List<Integer>, IDorHead> headSubstitutions;

		private UnifyingSubstitution(final Map<Integer, IDorHead> leftSubstitutions,
				final Map<Integer, IDorHead> rightSubstitutions,
				final Map<Integer, Preposition> leftPrepositionSubstitutions,
				final Map<Integer, Preposition> rightPrepositionSubstitutions,
				final Map<List<Integer>, IDorHead> headSubstitutions) {
			super();
			this.leftSubstitutions = leftSubstitutions;
			this.rightSubstitutions = rightSubstitutions;
			this.leftPrepositionSubstitutions = leftPrepositionSubstitutions;
			this.rightPrepositionSubstitutions = rightPrepositionSubstitutions;
			this.headSubstitutions = headSubstitutions;
		}

		public static UnifyingSubstitution makeHeadSubstitution(final List<Integer> headIndices, final IDorHead newHead) {
			return new UnifyingSubstitution(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
					Collections.emptyMap(), ImmutableMap.of(headIndices, newHead));
		}

		private static UnifyingSubstitution make(final Coindexation left, final Coindexation right,
				final boolean isConjunction) {
			final Map<Integer, IDorHead> leftSubstitutions = new HashMap<>();
			final Map<Integer, IDorHead> rightSubstitutions = new HashMap<>();

			final Map<Integer, Preposition> leftPrepositionSubstitutions = new HashMap<>();
			final Map<Integer, Preposition> rightPrepositionSubstitutions = new HashMap<>();

			final Map<List<Integer>, IDorHead> headSubstitutions = new HashMap<>();

			make(left, right, leftSubstitutions, rightSubstitutions, leftPrepositionSubstitutions,
					rightPrepositionSubstitutions, headSubstitutions, new AtomicInteger(0), isConjunction);
			return new UnifyingSubstitution(leftSubstitutions, rightSubstitutions, leftPrepositionSubstitutions,
					rightPrepositionSubstitutions, headSubstitutions);
		}

		private static void make(final Coindexation left, final Coindexation right,
				final Map<Integer, IDorHead> leftSubstitutions, final Map<Integer, IDorHead> rightSubstitutions,
				final Map<Integer, Preposition> leftPrepositionSubstitutions,
				final Map<Integer, Preposition> rightPrepositionSubstitutions,
				final Map<List<Integer>, IDorHead> headSubstitutions, final AtomicInteger freshID,
				final boolean isConjunction) {
			// Syntax should enforce that they're not both non-null
			final Preposition newLeftPreposition;
			final Preposition newRightPreposition;

			if (left.preposition == Preposition.UNSPECIFIED && right.preposition != Preposition.NONE
					&& right.preposition != Preposition.UNSPECIFIED) {
				newLeftPreposition = right.preposition;
				newRightPreposition = null;
			} else if (right.preposition == Preposition.UNSPECIFIED && left.preposition != Preposition.NONE
					&& left.preposition != Preposition.UNSPECIFIED) {
				newRightPreposition = left.preposition;
				newLeftPreposition = null;
			} else {
				newLeftPreposition = null;
				newRightPreposition = null;
			}

			if (right.idOrHead.head == null) {
				if (left.idOrHead.head == null) {
					// TODO urgh
					IDorHead id = leftSubstitutions.get(left.idOrHead.id);
					if (id == null) {
						id = rightSubstitutions.get(right.idOrHead.id);
					}

					if (id == null) {
						id = new IDorHead(freshID.decrementAndGet());
					}

					leftSubstitutions.put(left.idOrHead.id, id);
					rightSubstitutions.put(right.idOrHead.id, id);

					if (newLeftPreposition != null) {
						leftPrepositionSubstitutions.put(left.idOrHead.id, newLeftPreposition);
					}

					if (newRightPreposition != null) {
						rightPrepositionSubstitutions.put(right.idOrHead.id, newRightPreposition);
					}
				} else {
					rightSubstitutions.put(right.idOrHead.id, left.idOrHead);
					if (newRightPreposition != null) {
						rightPrepositionSubstitutions.put(right.idOrHead.id, newRightPreposition);
					}
				}
			} else {
				if (isConjunction) {
					final List<Integer> coordinatedHead = new ArrayList<>();
					coordinatedHead.addAll(right.idOrHead.head);
					if (left.idOrHead.head != null) {
						coordinatedHead.addAll(left.idOrHead.head);
					} else {
						Util.debugHook();
					}
					// leftSubstitutions.put(left.idOrHead.id, new IDorHead(coordinatedHead));
					headSubstitutions.put(left.idOrHead.head, new IDorHead(coordinatedHead));
					headSubstitutions.put(right.idOrHead.head, new IDorHead(coordinatedHead));
				} else {
					leftSubstitutions.put(left.idOrHead.id, right.idOrHead);
				}

				if (newLeftPreposition != null) {
					leftPrepositionSubstitutions.put(left.idOrHead.id, newLeftPreposition);
				}
			}

			if (left.left != null) {
				make(left.left, right.left, leftSubstitutions, rightSubstitutions, leftPrepositionSubstitutions,
						rightPrepositionSubstitutions, headSubstitutions, freshID, isConjunction);
			}
			if (left.right != null) {
				make(left.right, right.right, leftSubstitutions, rightSubstitutions, leftPrepositionSubstitutions,
						rightPrepositionSubstitutions, headSubstitutions, freshID, isConjunction);
			}
		}

		private Dependency applyTo(final UnresolvedDependency dep, final boolean isLeft) {
			final Dependency result = applyTo(dep, isLeft ? leftSubstitutions : rightSubstitutions,
					isLeft ? leftPrepositionSubstitutions : rightPrepositionSubstitutions);
			Preconditions.checkState(result.getCategory() == dep.getCategory());
			return result;
		}

		private Dependency applyTo(UnresolvedDependency dep, final Map<Integer, IDorHead> substitutions,
				final Map<Integer, Preposition> prepositionSubstitutions) {

			final Preposition prep = prepositionSubstitutions.get(dep.argumentID);
			if (prep != null) {
				dep = dep.setPreposition(prep);
			}

			final IDorHead result = substitutions.get(dep.argumentID);
			if (result == null) {
				return dep;
			} else if (result.isHead()) {
				return dep.resolve(result.head);
			} else {
				return dep.resolve(result.id);
			}
		}

		public Coindexation apply(final Coindexation other) {
			return applyTo(other, true);
		}

		private Coindexation applyTo(final Coindexation arg, final boolean isLeft) {
			return applyTo(arg, isLeft ? leftSubstitutions : rightSubstitutions, isLeft ? leftPrepositionSubstitutions
					: rightPrepositionSubstitutions);
		}

		private Coindexation applyTo(final Coindexation arg, final Map<Integer, IDorHead> substitutions,
				final Map<Integer, Preposition> prepositionSubstitutions) {
			if (arg == null) {
				return null;
			}
			final IDorHead newID = arg.idOrHead.id != null ? substitutions.get(arg.idOrHead.id) : headSubstitutions
					.get(arg.idOrHead.head);
			if (arg.left == null && arg.right == null) {
				final Preposition newPrep = prepositionSubstitutions.get(arg.idOrHead.id);
				return new Coindexation(newID != null ? newID : arg.idOrHead, newPrep != null ? newPrep
						: arg.preposition);

			} else {
				return new Coindexation(applyTo(arg.left, substitutions, prepositionSubstitutions), applyTo(arg.right,
						substitutions, prepositionSubstitutions), newID != null ? newID : arg.idOrHead);

			}

		}
	}

	public DependencyStructure compose2(DependencyStructure other,
			final List<UnlabelledDependency> newResolvedDependencies) {
		// A/B (B/C)/D ---> (A/C)/D
		other = standardizeApart(other, coindexation.getMaxID() + 1);

		final UnifyingSubstitution substitution = UnifyingSubstitution.make(coindexation.right,
				other.coindexation.left.left, false);

		final Set<UnresolvedDependency> newUnresolvedDependencies = new HashSet<>();

		updateResolvedDependencies(other, substitution, newUnresolvedDependencies, newResolvedDependencies);
		final Set<UnresolvedDependency> normalizedUnresolvedDependencies = new HashSet<>(
				newUnresolvedDependencies.size());
		final Coindexation normalizedArgument;
		if (coindexation.isModifier()) {
			// X/X X/Y/Z
			normalizedArgument = normalize(substitution.applyTo(other.coindexation, false), newUnresolvedDependencies,
					normalizedUnresolvedDependencies, newResolvedDependencies, 1);
		} else {
			// S\NP/NP NP/PP/PP
			final Coindexation leftWithSubstitution = substitution.applyTo(coindexation, true);
			final Coindexation rightWithSubstitution = substitution.applyTo(other.coindexation, false);
			normalizedArgument = normalize(new Coindexation(new Coindexation(leftWithSubstitution.left,
					rightWithSubstitution.left.right, leftWithSubstitution.idOrHead), rightWithSubstitution.right,
					leftWithSubstitution.idOrHead), newUnresolvedDependencies, normalizedUnresolvedDependencies,
					newResolvedDependencies, 1);
		}

		return new DependencyStructure(normalizedArgument, normalizedUnresolvedDependencies);
	}

	public DependencyStructure specifyPreposition(final Preposition preposition) {
		return new DependencyStructure(coindexation.specifyPreposition(preposition), getUnresolvedDependencies());
	}

}