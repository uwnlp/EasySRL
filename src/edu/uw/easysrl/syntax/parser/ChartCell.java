package edu.uw.easysrl.syntax.parser;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.util.FastTreeMap;

public abstract class ChartCell {
	/**
	 * Possibly adds a @AgendaItem to this chart cell. Returns true if the parse was added, and false if the cell was
	 * unchanged.
	 */
	public final boolean add(final AgendaItem entry) {
		return add(entry.getEquivalenceClassKey(), entry);
	}

	public static abstract class ChartCellFactory {
		public abstract ChartCell make();

		/**
		 * Get factory for a new sentence.
		 */
		public ChartCellFactory forNewSentence() {
			return this;
		}
	}

	public abstract boolean add(final Object key, final AgendaItem entry);

	public abstract Iterable<AgendaItem> getEntries();

	public abstract int size();

	/**
	 * Chart Cell used for 1-best parsing.
	 */
	protected static class Cell1Best extends ChartCell {
		final Map<Object, AgendaItem> keyToProbability = new HashMap<>();

		@Override
		public Collection<AgendaItem> getEntries() {
			return keyToProbability.values();
		}

		@Override
		public boolean add(final Object key, final AgendaItem entry) {
			return keyToProbability.putIfAbsent(key, entry) == null;
		}

		@Override
		public int size() {
			return keyToProbability.size();
		}

		public static ChartCellFactory factory() {
			return new ChartCellFactory() {

				@Override
				public ChartCell make() {
					return new Cell1Best();
				}
			};
		}
	}

	/**
	 * ChartCell for A* parsing that uses a custom tree data structure, rather than a hash map. It'll die horribly if
	 * the keys aren't comparable.
	 */
	protected static class Cell1BestTreeBased extends ChartCell {
		final FastTreeMap<Object, AgendaItem> keyToProbability = new FastTreeMap<>();

		@Override
		public Iterable<AgendaItem> getEntries() {
			return keyToProbability.values();
		}

		@Override
		public boolean add(final Object key, final AgendaItem entry) {
			return keyToProbability.putIfAbsent(key, entry);
		}

		@Override
		public int size() {
			return keyToProbability.size();
		}

		public static ChartCellFactory factory() {
			return new ChartCellFactory() {

				@Override
				public ChartCell make() {
					return new Cell1BestTreeBased();
				}
			};
		}
	}

	/**
	 * ChartCell for CKY parsing. The main difference with A* is that it needs to check if new entries have a higher
	 * score than existing entries (which can't happen with A*).
	 *
	 */
	protected static class Cell1BestCKY extends Cell1Best {
		@Override
		public Collection<AgendaItem> getEntries() {
			return keyToProbability.values();
		}

		@Override
		public boolean add(final Object key, final AgendaItem entry) {
			final AgendaItem currentEntry = keyToProbability.get(key);
			if (currentEntry == null || entry.getInsideScore() > currentEntry.getInsideScore()) {
				keyToProbability.put(key, entry);
				return true;
			} else {
				return false;
			}
		}

	}

	/**
	 * Allows a limited or unbounded number of items in a cell, without dividing them into equivalence classes.
	 *
	 * Could also be used in conjunction with dependency hashing?
	 */
	static class CellNoDynamicProgram extends ChartCell {
		private final Collection<AgendaItem> entries;

		CellNoDynamicProgram() {
			this.entries = new ArrayList<>();
		}

		CellNoDynamicProgram(int nbest) {
			this.entries = MinMaxPriorityQueue.maximumSize(nbest).create();
		}

		@Override
		public Collection<AgendaItem> getEntries() {
			return entries;
		}

		@Override
		public boolean add(final Object key, final AgendaItem newEntry) {
			return entries.add(newEntry);
		}

		@Override
		public int size() {
			return entries.size();
		}

		public static ChartCellFactory factory() {
			return new ChartCellFactory() {

				@Override
				public ChartCell make() {
					return new CellNoDynamicProgram();
				}
			};
		}

		public static ChartCellFactory factory(final int nbest) {
			return new ChartCellFactory() {

				@Override
				public ChartCell make() {
					return new CellNoDynamicProgram(nbest);
				}
			};
		}
	}

	/**
	 * Implements dependency hashing for better N-best parsing, as in Ng&Curran 2012
	 */
	public static class ChartCellNbestFactory extends ChartCellFactory {

		private final int nbest;
		private final double nbestBeam;

		public ChartCellNbestFactory(final int nbest, final double nbestBeam, final int maxSentenceLength,
				final Collection<Category> categories) {
			super();
			this.nbest = nbest;
			this.nbestBeam = nbestBeam;
			final Random randomGenerator = new Random();

			// Build a hash for every possible dependency
			categoryToArgumentToHeadToModifierToHash = HashBasedTable.create();
			for (final Category c : categories) {
				for (int i = 1; i <= c.getNumberOfArguments(); i++) {
					final int[][] array = new int[maxSentenceLength][maxSentenceLength];
					categoryToArgumentToHeadToModifierToHash.put(c, i, array);
					for (int head = 0; head < maxSentenceLength; head++) {
						for (int child = 0; child < maxSentenceLength; child++) {
							array[head][child] = randomGenerator.nextInt();
						}
					}
				}
			}
		}

		public ChartCellNbestFactory(ChartCellNbestFactory other) {
			this.nbest = other.nbest;
			this.nbestBeam = other.nbestBeam;
			this.categoryToArgumentToHeadToModifierToHash = other.categoryToArgumentToHeadToModifierToHash;
		}

		// A cache of hash scores for nodes, to save recomputing them. I'm not in love with this design, but at least it
		// keeps all the hashing code in one place.
		private final Map<SyntaxTreeNode, Integer> nodeToHash = new HashMap<>();
		private final Table<Category, Integer, int[][]> categoryToArgumentToHeadToModifierToHash;

		private int getHash(final SyntaxTreeNode parse) {
			Integer result = nodeToHash.get(parse);
			if (result == null) {
				result = 0;

				// Add in a hash for each dependency at this node.
				final List<UnlabelledDependency> resolvedUnlabelledDependencies = parse
						.getResolvedUnlabelledDependencies();
				if (resolvedUnlabelledDependencies != null) {
					for (final UnlabelledDependency dep : resolvedUnlabelledDependencies) {
						for (final int arg : dep.getArguments()) {
							if (dep.getHead() != arg) {
								result = result
										^ categoryToArgumentToHeadToModifierToHash.get(dep.getCategory(),
												dep.getArgNumber())[dep.getHead()][arg];
							}
						}

					}
				}

				for (final SyntaxTreeNode child : parse.getChildren()) {
					result = result ^ getHash(child);
				}
			}

			return result;
		}

		/**
		 * Chart Cell used for N-best parsing. It allows multiple entries with the same key, but doesn't check for
		 * equivalence
		 */
		protected class CellNBest extends ChartCell {
			private final ListMultimap<Object, AgendaItem> keyToEntries = ArrayListMultimap.create();

			@Override
			public Collection<AgendaItem> getEntries() {
				return keyToEntries.values();
			}

			@Override
			public boolean add(final Object key, final AgendaItem newEntry) {
				final List<AgendaItem> existing = keyToEntries.get(key);
				if (existing.size() > nbest
						|| (existing.size() > 0 && newEntry.getCost() < existing.get(0).getCost() + Math.log(nbestBeam))) {
					return false;
				} else {
					// Only cache out hashes for nodes that get added to the chart.
					keyToEntries.put(key, newEntry);
					return true;
				}

			}

			@Override
			public int size() {
				return keyToEntries.size();
			}
		}

		/**
		 * Chart Cell used for N-best parsing. It allows multiple entries with the same key, if they are not equivalent.
		 */
		class CellNBestWithHashing extends ChartCell {
			private final ListMultimap<Object, AgendaItem> keyToEntries = ArrayListMultimap.create();
			private final Multimap<Object, Integer> keyToHash = HashMultimap.create();

			@Override
			public Collection<AgendaItem> getEntries() {
				return keyToEntries.values();
			}

			@Override
			public boolean add(final Object key, final AgendaItem newEntry) {

				final List<AgendaItem> existing = keyToEntries.get(key);
				if (existing.size() > nbest
						|| (existing.size() > 0 && newEntry.getCost() < existing.get(0).getCost() + Math.log(nbestBeam))) {
					return false;
				} else {
					final Integer hash = getHash(newEntry.getParse());
					if (keyToHash.containsEntry(key, hash)) {
						// Already have an equivalent node.
						return false;
					}

					keyToEntries.put(key, newEntry);
					keyToHash.put(key, hash);

					// Cache out hash for this parse.
					nodeToHash.put(newEntry.getParse(), hash);
					return true;
				}
			}

			@Override
			public int size() {
				return keyToEntries.size();
			}
		}

		@Override
		public ChartCell make() {
			return // new CellNBest();
			new CellNBestWithHashing();
		}

		@Override
		public ChartCellFactory forNewSentence() {
			return new ChartCellNbestFactory(this);
		}
	}

}
