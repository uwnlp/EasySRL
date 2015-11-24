package edu.uw.easysrl.dependencies;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.carrotsearch.hppc.IntIntHashMap;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.util.Util;

public class Coindexation implements Serializable {

	/**
	 * Represents the endpoint of a dependency. For fully-specified dependencies, this will be a 'head'. Multiple heads
	 * are possible to deal with coordination. Otherwise, it will be an 'id', referring to some coindexation.
	 */
	public static class IDorHead implements Serializable {

		private static final long serialVersionUID = -2338555345725737539L;
		final List<Integer> head;
		final Integer id;

		public boolean isHead() {
			return head != null;
		}

		IDorHead(final List<Integer> head) {
			this.head = head;
			this.id = null;
		}

		IDorHead(final Integer id) {
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

	/**
	 *
	 */
	private static final long serialVersionUID = 1201129160464966099L;

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

	Coindexation normalize(final IntIntHashMap substitutions, final int minValue) {
		Coindexation.IDorHead newIDorHead = idOrHead;
		if (idOrHead.id != null) {
			int newID = substitutions.getOrDefault(idOrHead.id, Integer.MIN_VALUE);
			if (newID == Integer.MIN_VALUE) {
				newID = substitutions.size() + minValue;
				substitutions.put(idOrHead.id, newID);
			}
			newIDorHead = new Coindexation.IDorHead(newID);
		}

		return new Coindexation(left == null ? null : left.normalize(substitutions, minValue), right == null ? null
				: right.normalize(substitutions, minValue), newIDorHead, preposition);
	}

	Coindexation specifyPreposition(final Preposition newPreposition) {
		return new Coindexation(left == null ? null : left.specifyPreposition(newPreposition), right == null ? null
				: right.specifyPreposition(newPreposition), idOrHead,
				preposition == Preposition.UNSPECIFIED ? newPreposition : preposition);
	}

	@Override
	public int hashCode() {
		return Objects.hash(left, right, idOrHead, preposition);

	}

	@Override
	public boolean equals(final Object obj) {
		final Coindexation other = (Coindexation) obj;
		return other != null && Objects.equals(idOrHead, other.idOrHead)
				&& Objects.equals(preposition, other.preposition) && Objects.equals(left, other.left)
				&& Objects.equals(right, other.right);

	}

	final Coindexation.IDorHead idOrHead;
	final Coindexation left;
	final Coindexation right;
	final Preposition preposition;

	@Override
	public String toString() {
		if (left == null) {
			return idOrHead.toString();
		} else if (right == null) {
			return "(" + right + ")_" + idOrHead;
		} else {
			return "(" + left + "|" + right + ")_" + idOrHead;
		}
	}

	Coindexation(final Coindexation.IDorHead head, final Preposition preposition) {
		this(null, null, head, preposition);
	}

	Coindexation(final Coindexation left, final Coindexation right, final Coindexation.IDorHead head) {
		this(left, right, head, Preposition.NONE);
	}

	public Coindexation.IDorHead getID() {
		return idOrHead;
	}

	int countNumberOfArguments() {
		if (right == null) {
			return 0;
		} else {
			return left.countNumberOfArguments() + 1;
		}

	}

	/**
	 * Checks if this can be a correct coindexation for a category. In particular, it looks for cases like
	 * (X_1\X_1)_2/Y_3, where the result of an application isn't properly headed. Basically, any ID used on the 'spine'
	 * should have already been used somewhere to the right.
	 *
	 * Useful for debugging.
	 */
	boolean validate() {
		final Set<Integer> idsUsedSoFar = new HashSet<>();

		Coindexation coindexation = this;
		while (coindexation != null) {

			if (!coindexation.idOrHead.isHead() && !idsUsedSoFar.contains(coindexation.idOrHead.id)) {
				return false;
			}
			if (coindexation.right != null) {
				coindexation.right.getIDs(idsUsedSoFar);
			}
			coindexation = coindexation.left;
		}

		return true;
	}

	private void getIDs(final Set<Integer> idCount) {
		if (!idOrHead.isHead()) {
			idCount.add(idOrHead.id);
		}
		if (left != null) {
			left.getIDs(idCount);
		}

		if (right != null) {
			right.getIDs(idCount);
		}
	}

	private Coindexation(final Coindexation left, final Coindexation right, final Coindexation.IDorHead idOrHead,
			final Preposition preposition) {
		super();
		this.idOrHead = idOrHead;
		this.left = left;
		this.right = right;
		this.preposition = preposition;

	}

	public Preposition getPreposition() {
		return preposition;
	}

	public Coindexation getRight() {
		return right;
	}

	private static Coindexation.IDorHead getID(final Coindexation.IDorHead defaultHead,
			final Map<Integer, Integer> usedIDs, final Integer ccgbankID) {
		Coindexation.IDorHead idOrHead;
		if (ccgbankID != null) {
			// Negate CCGBank ID's to avoid clashes with automatically generated
			// ones.
			Integer id = usedIDs.get(-ccgbankID);
			if (id == null) {
				id = usedIDs.size() + 1;
				usedIDs.put(-ccgbankID, id);
			}

			idOrHead = new Coindexation.IDorHead(id);
		} else {
			// NP
			if (defaultHead != null) {
				idOrHead = defaultHead;
			} else {
				final int id = usedIDs.size() + 1;
				usedIDs.put(id, id);
				idOrHead = new Coindexation.IDorHead(id);
			}

		}
		return idOrHead;
	}

	static Coindexation fromString(final Category category2, final int wordIndex) {
		String category = categoryToMarkedUpCategory.get(category2);
		if (category == null) {
			category = category2.toString();
		}
		return fromString(category, new Coindexation.IDorHead(Collections.singletonList(wordIndex)), new HashMap<>(),
				wordIndex, true);
	}

	public static Coindexation fromString(final String category, final int wordIndex) {
		return fromString(category, new Coindexation.IDorHead(Collections.singletonList(wordIndex)), new HashMap<>(),
				wordIndex, true);
	}

	static Coindexation fromString(String category, final Coindexation.IDorHead defaultHead,
			final Map<Integer, Integer> usedIDs, final int headWord, final boolean isOnSpine) {

		if (category.endsWith("}") && !category.endsWith("{_*}")) {
			final int openIndex = category.lastIndexOf("{");
			category = category.substring(0, openIndex);
		}
		final int slashIndex = Util.indexOfAny(category, "/\\");

		if (slashIndex == -1) {
			Coindexation.IDorHead idOrHead;
			// NP_4
			final String[] fields = category.split("_");

			if (category.endsWith("{_*}")) {
				// Used on categories like
				// his NP_1/(N_1/PP{_*})_1
				// To indicate that the PP is headed by "his"
				idOrHead = new Coindexation.IDorHead(Arrays.asList(headWord));
			} else if (fields.length == 1) {
				idOrHead = Coindexation.getID(defaultHead, usedIDs, null);
			} else {
				final Integer ccgbankID = Integer.valueOf(fields[1]);
				idOrHead = Coindexation.getID(defaultHead, usedIDs, ccgbankID);
			}

			// Using "startsWith" because it's easier when categories have extra
			// annotation
			final Preposition preposition = fields[0].startsWith("PP") ? Preposition.UNSPECIFIED : Preposition.NONE;

			// PP[of]

			return new Coindexation(idOrHead, preposition);
		} else {
			// (X_i/Y_j)_k/(Y_j\Z_k)_z
			// ((X_i/Y_j)_k/(Y_j\Z_k)_z)_x

			Coindexation.IDorHead idOrHead = null;

			int nonNestedSlashIndex = Util.findNonNestedChar(category, "/\\");

			if (nonNestedSlashIndex == -1) {
				// ((X_i/Y_j)_k/(Y_j\Z_k)_z)_x
				final int closeBracket = Util.findClosingBracket(category);
				Integer ccgbankID;
				if (closeBracket == category.length() - 1) {
					ccgbankID = null;

				} else {
					if (category.charAt(closeBracket + 1) != '_') {
						throw new RuntimeException("Error reading category: " + category);
					}

					ccgbankID = Integer.valueOf(category.substring(closeBracket + 2));

				}

				category = category.substring(1, closeBracket);

				if (ccgbankID != null) {
					idOrHead = Coindexation.getID(defaultHead, usedIDs, ccgbankID);
				}
				nonNestedSlashIndex = Util.findNonNestedChar(category, "/\\");

			}

			final String l = category.substring(0, nonNestedSlashIndex);
			final String r = category.substring(nonNestedSlashIndex + 1);

			final Coindexation left = fromString(l, defaultHead, usedIDs, headWord, isOnSpine);
			final Coindexation right = fromString(r, null, usedIDs, headWord, false);

			if (idOrHead == null) {
				if (isOnSpine) {
					// Words taking a modifier as an argument should have a
					// dependency to the modifier, not its head.
					// (S_i/S_i)_possibly
					idOrHead = defaultHead;
				} else {
					// Unlabeled heads of complex arguments. e.g. N/(N/PP) defaults to N/(N_1/PP_2)_3
					idOrHead = Coindexation.getID(null, usedIDs, null);
				}
			}

			return new Coindexation(left, right, idOrHead, Preposition.NONE);
		}
	}

	public Coindexation getLeft() {
		return left;
	}

	public boolean isModifier() {
		return left.equals(right);
	}

	public Coindexation getLeftMost() {
		if (left == null) {
			return this;
		} else {
			return left.getLeftMost();
		}
	}

	int getMaxID() {
		int result = 0;
		if (!idOrHead.isHead()) {
			result = idOrHead.getID();
		}

		if (left != null) {
			result = Math.max(result, left.getMaxID());
		}
		if (right != null) {
			result = Math.max(result, right.getMaxID());
		}
		return result;
	}
}