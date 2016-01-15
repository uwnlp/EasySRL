package edu.uw.easysrl.syntax.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.training.CKY.ChartCell;
import edu.uw.easysrl.syntax.training.CKY.EquivalenceClassKey;
import edu.uw.easysrl.syntax.training.CKY.EquivalenceClassValue;

/**
 * Alternative representation of a parse chart, that allows us to collapse various distinctions that are needed for
 * parsing, but not modelling. For example, parsing needs to keep track of rule types for normal-form constraints, but
 * we don't use that for features. TODO merge this with FeatureForest
 *
 */
class CompressedChart {

	private final List<InputWord> words;
	private final Collection<Key> roots;

	CompressedChart(final List<InputWord> words, final Collection<Key> roots) {
		super();
		this.words = words;
		this.roots = roots;
	}

	static class Key {
		final Category category;
		final RuleType ruleClass;

		final int startIndex;
		final int lastIndex;
		final Set<Value> values;

		Key(final Category category, final int startIndex, final int lastIndex, final RuleType ruleClass,
				final Set<Value> values) {
			super();
			this.values = values;
			this.category = category;
			this.ruleClass = ruleClass;
			this.startIndex = startIndex;
			this.lastIndex = lastIndex;

		}

		private Integer hashCode = null;

		@Override
		public int hashCode() {
			if (hashCode == null) {
				hashCode = hashCode2();
			}
			return hashCode;
		}

		private int hashCode2() {
			return Objects.hash(values, category, ruleClass, startIndex, lastIndex);
		}

		@Override
		public boolean equals(final Object obj) {

			final Key other = (Key) obj;

			return this == other
					|| (values.equals(other.values) && category == other.category && ruleClass.equals(other.ruleClass)
							&& startIndex == other.startIndex && lastIndex == other.lastIndex);

		}

		public Collection<Value> getChildren() {
			return values;
		}

		public int getLastIndex() {
			return lastIndex;

		}

		public int getStartIndex() {
			return startIndex;
		}

	}

	static abstract class Value {
		public Value() {
			super();
		}

		private transient Integer hashCode = null;

		@Override
		public final int hashCode() {

			if (hashCode == null) {
				hashCode = hashCode2();
			}
			return hashCode;
		}

		abstract int hashCode2();

		@Override
		public abstract boolean equals(final Object obj);

		public abstract List<Key> getChildren();

		public abstract Set<ResolvedDependency> getDependencies();

		public abstract Category getCategory();

		public abstract int getIndex();

		public int getStartIndex() {
			final List<Key> children = getChildren();

			return children.size() == 0 ? getIndex() : children.get(0).getStartIndex();
		}

		public int getLastIndex() {
			final List<Key> children = getChildren();

			return (children.size() == 0 ? getIndex() : children.get(children.size() - 1).getLastIndex());

		}

		public int getRuleID() {
			throw new UnsupportedOperationException();
		}

	}

	static class CategoryValue extends Value {
		private final Category category;
		private final int index;

		CategoryValue(final Category category, final int index) {
			super();
			this.category = category;
			this.index = index;
		}

		@Override
		public int hashCode2() {
			return Objects.hash(category, index);
		}

		@Override
		public boolean equals(final Object obj) {
			if (!(obj instanceof CategoryValue)) {
				return false;
			}

			final CategoryValue other = (CategoryValue) obj;
			return index == other.index && category.equals(other.category);
		}

		@Override
		public Set<ResolvedDependency> getDependencies() {
			return Collections.emptySet();
		}

		@Override
		public Category getCategory() {
			return category;
		}

		@Override
		public int getIndex() {
			return index;
		}

		@Override
		public List<Key> getChildren() {
			return Collections.emptyList();
		}

	}

	static class TreeValueBinary extends TreeValue {
		private final Key left;
		private final Key right;

		TreeValueBinary(final Key left, final Key right, final Set<ResolvedDependency> deps) {
			super(deps);
			this.left = left;
			this.right = right;
		}

		@Override
		public List<Key> getChildren() {
			return Arrays.asList(left, right);
		}

		@Override
		int hashCode2() {
			final int prime = 31;
			int result = 1;
			result = prime * result + left.hashCode();
			result = prime * result + right.hashCode();
			result = prime * result + super.hashCode2();
			return result;
		}

		@Override
		public boolean equals(final Object obj) {

			if (!(obj instanceof TreeValueBinary)) {
				return false;
			}

			final TreeValueBinary other = (TreeValueBinary) obj;

			return left.equals(other.left) && right.equals(other.right) && super.equals(other);

		}

	}

	static class TreeValueUnary extends TreeValue {
		private final Key child;
		private final int ruleID;

		TreeValueUnary(final Key child, final int ruleID, final Set<ResolvedDependency> deps) {
			super(deps);
			this.child = child;
			this.ruleID = ruleID;
		}

		@Override
		public List<Key> getChildren() {
			return Arrays.asList(child);
		}

		@Override
		public int getRuleID() {
			return ruleID;
		}

		@Override
		int hashCode2() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ruleID;
			result = prime * result + child.hashCode();
			result = prime * result + super.hashCode2();
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (!(obj instanceof TreeValueUnary)) {
				return false;
			}

			final TreeValueUnary other = (TreeValueUnary) obj;
			return ruleID == other.ruleID && child.equals(other.child) && super.equals(other);
		}

	}

	static abstract class TreeValue extends Value {
		private final Set<ResolvedDependency> deps;

		private TreeValue(final Set<ResolvedDependency> deps) {
			this.deps = deps;
		}

		@Override
		public abstract List<Key> getChildren();

		@Override
		public final Set<ResolvedDependency> getDependencies() {
			return deps;
		}

		@Override
		public Category getCategory() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getIndex() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean equals(final Object obj) {
			if (!(obj instanceof TreeValue)) {
				return false;
			}

			final TreeValue other = (TreeValue) obj;

			return deps.equals(other.deps);
		}

		@Override
		int hashCode2() {
			return deps.hashCode();
		}
	}

	public Set<ResolvedDependency> getAllDependencies() {
		final Set<ResolvedDependency> result = new HashSet<>();
		final Set<Key> visited = new HashSet<>();
		for (final Key root : roots) {
			getAllDependencies(root, result, visited);
		}
		return result;
	}

	private void getAllDependencies(final Key key, final Set<ResolvedDependency> result, final Set<Key> visited) {
		if (visited.contains(key)) {
			return;
		}

		visited.add(key);

		for (final Value value : key.getChildren()) {
			for (final ResolvedDependency dep : value.getDependencies()) {
				result.add(dep);
			}

			for (final Key child : value.getChildren()) {
				getAllDependencies(child, result, visited);
			}
		}

	}

	private static Key make(final ChartCell[][] chart, final int spanStart, final int spanLength,
			final EquivalenceClassKey key, final Map<Object, Key> cache, final Map<Object, Value> valueCache,
			final CutoffsDictionaryInterface cutoffs, final Multimap<Category, UnaryRule> unaryRules) {

		final ChartCell cell = chart[spanStart][spanLength - 1];

		Key result = cache.get(key);
		if (result == null) {

			final Collection<EquivalenceClassValue> entries = cell.getEntries(key);
			final Set<Value> values = new HashSet<>(entries.size());

			for (final EquivalenceClassValue value : entries) {
				final Set<ResolvedDependency> deps = new HashSet<>(value.getResolvedDependencies().size());
				for (final UnlabelledDependency dependency : value.getResolvedDependencies()) {
					if (cutoffs != null
							&& !cutoffs.isFrequentWithAnySRLLabel(dependency.getCategory(), dependency.getArgNumber())) {
						continue;
					}

					// Because we only model
					deps.add(new ResolvedDependency(dependency.getHead(), dependency.getCategory(), dependency
							.getArgNumber(), dependency.getArguments().get(0), SRLFrame.UNLABELLED_ARGUMENT, dependency
							.getPreposition()));
				}

				int start = spanStart;
				final List<EquivalenceClassKey> uncompressedChildren = value.getChildren();
				final List<Key> children = new ArrayList<>(uncompressedChildren.size());
				for (final EquivalenceClassKey child : uncompressedChildren) {
					children.add(make(chart, start, child.length(), child, cache, valueCache, cutoffs, unaryRules));
					start += child.length();
				}

				Value newVal;
				if (children.isEmpty()) {
					newVal = new CategoryValue(key.getCategory(), spanStart);

				} else if (children.size() == 1) {
					Preconditions.checkState(uncompressedChildren.size() == 1);
					final Category from = uncompressedChildren.get(0).getCategory();

					final Category to = key.getCategory();
					Integer ruleID = null;
					for (final UnaryRule unary : unaryRules.get(from)) {
						if (to.equals(unary.getCategory())) {
							ruleID = unary.getID();
						}
					}

					newVal = new TreeValueUnary(children.get(0), ruleID, deps);

				} else {
					newVal = new TreeValueBinary(children.get(0), children.get(1), deps);
				}

				final Value existing = valueCache.get(newVal);
				if (existing != null) {
					newVal = existing;
				} else {
					valueCache.put(newVal, newVal);
				}

				values.add(newVal);

			}

			result = new Key(key.getCategory(), spanStart, spanStart + spanLength - 1, key.getRuleType(), values);
			cache.put(key, result);
		}

		return result;
	}

	static CompressedChart make(final List<InputWord> words, final ChartCell[][] chart,
			final CutoffsDictionaryInterface cutoffs, final Multimap<Category, UnaryRule> unaryRules,
			final Collection<Category> rootCategories) {
		final ChartCell cell = chart[0][chart.length - 1];
		final Collection<Key> roots = new HashSet<>();
		final Map<Object, Value> valueCache = new HashMap<>();
		final Map<Object, Key> keyCache = new IdentityHashMap<>();

		for (final EquivalenceClassKey entry : cell.getKeys()) {

			if (rootCategories.contains(entry.getCategory())) {
				roots.add(make(chart, 0, chart.length, entry, keyCache, valueCache, cutoffs, unaryRules));
			}
		}

		if (roots.size() == 0) {
			return null;
		}

		return new CompressedChart(words, ImmutableSet.copyOf(roots));

	}

	public Collection<Key> getRoots() {
		return roots;
	}

	public List<InputWord> getWords() {
		return words;
	}

	public int getNumberOfPossibleParses() {
		int result = 0;
		final Map<Key, Integer> cache = new HashMap<>();
		for (final Key root : roots) {
			result += getNumberOfPossibleParses(root, cache);

		}

		return result;
	}

	private int getNumberOfPossibleParses(final Key key, final Map<Key, Integer> cache) {
		Integer result = cache.get(key);
		if (result == null) {
			result = 0;
			for (final Value value : key.getChildren()) {
				int resultForValue = 1;
				for (final Key child : value.getChildren()) {
					resultForValue = resultForValue * getNumberOfPossibleParses(child, cache);
				}

				result += resultForValue;
			}

			cache.put(key, result);
		}
		return result;
	}
}
