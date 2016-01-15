package edu.uw.easysrl.syntax.training;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.syntax.model.feature.ArgumentSlotFeature;
import edu.uw.easysrl.syntax.model.feature.BilexicalFeature;
import edu.uw.easysrl.syntax.model.feature.Feature.BinaryFeature;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.model.feature.Feature.RootCategoryFeature;
import edu.uw.easysrl.syntax.model.feature.Feature.UnaryRuleFeature;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.model.feature.PrepositionFeature;
import edu.uw.easysrl.syntax.training.CompressedChart.Key;
import edu.uw.easysrl.syntax.training.CompressedChart.Value;

class FeatureForest {

	private final Collection<ConjunctiveNode> conjunctiveNodes = new ArrayList<>();

	private final Collection<DisjunctiveNode> roots;
	private final CutoffsDictionaryInterface cutoffsDictionary;
	private final List<InputWord> words;

	FeatureForest(final List<InputWord> words, final CompressedChart allParses,
			final CutoffsDictionaryInterface cutoffsDictionary) {
		final Collection<DisjunctiveNode> result = new ArrayList<>();

		this.words = words;
		this.cutoffsDictionary = cutoffsDictionary;
		final Map<Key, DisjunctiveNode> cache1 = new HashMap<>();
		final Map<ResolvedDependency, DisjunctiveNode> dependencyNodeCache = new HashMap<>();
		final Map<Value, ConjunctiveNode> conjuctiveNodeCache = new IdentityHashMap<>();
		for (final Key key : allParses.getRoots()) {
			result.add(parseDisjunctive(key, cache1, dependencyNodeCache, conjuctiveNodeCache));
		}

		this.roots = result;
	}

	private ConjunctiveNode parseConjunctive(final Value value, final Map<Key, DisjunctiveNode> disjuctiveNodeCache,
			final Map<Value, ConjunctiveNode> conjuctiveNodeCache,
			final Map<ResolvedDependency, DisjunctiveNode> dependencyNodeCache, final DisjunctiveTreeNode parent) {

		if (conjuctiveNodeCache.containsKey(value)) {
			return conjuctiveNodeCache.get(value);
		}

		final ConjunctiveNode result;

		final List<DisjunctiveNode> children = new ArrayList<>(value.getChildren().size()
				+ value.getDependencies().size());

		for (final Key child : value.getChildren()) {
			final DisjunctiveNode newChild = parseDisjunctive(child, disjuctiveNodeCache, dependencyNodeCache,
					new IdentityHashMap<>());
			children.add(newChild);
		}

		final int numberOfNonDependencyChildren = children.size();

		for (final ResolvedDependency dep : value.getDependencies()) {

			DisjunctiveNode possibleLabelsForDep = dependencyNodeCache.get(dep);

			if (possibleLabelsForDep == null) {
				possibleLabelsForDep = parseDependency(dep);
				dependencyNodeCache.put(dep, possibleLabelsForDep);
			}

			if (possibleLabelsForDep.getChildren().size() > 0) {
				children.add(possibleLabelsForDep);
			}

		}

		if (numberOfNonDependencyChildren == 0) {
			Preconditions.checkState(children.size() == 0);
			result = new ConjunctiveLexicalNode(value.getCategory(), value.getIndex(), parent);
			conjunctiveNodes.add(result);
		} else if (numberOfNonDependencyChildren == 1) {
			result = new ConjunctiveUnaryNode(parent, children, value.getRuleID());
			conjunctiveNodes.add(result);
		} else if (numberOfNonDependencyChildren == 2) {

			result = new ConjunctiveBinaryNode(parent, children);
			conjunctiveNodes.add(result);
		} else {
			throw new RuntimeException();
		}

		conjuctiveNodeCache.put(value, result);
		return result;
	}

	private DisjunctiveNode parseDependency(final ResolvedDependency dep) {
		final DisjunctiveNode parent = new DisjunctiveNode();
		final Collection<ConjunctiveDependencyNode> possibleLabels = new ArrayList<>();

		if (dep.getOffset() == 0 || // Unrealized arguments. Make the labels latent.
				// Unlabelled arguments are those which are under-specified from
				// the training charts
				dep.getSemanticRole() == SRLFrame.UNLABELLED_ARGUMENT) {

			// Underspecified labels.
			for (final SRLLabel label : cutoffsDictionary.getRoles(words.get(dep.getHead()).word, dep.getCategory(),
					dep.getPreposition(), dep.getArgNumber())) {
				if (cutoffsDictionary.isFrequent(dep.getCategory(), dep.getArgNumber(), label)) {
					final ResolvedDependency labelledDep = dep.overwriteLabel(label);

					possibleLabels.add(new ConjunctiveDependencyNode(labelledDep, parent, cutoffsDictionary.isFrequent(
							label, dep.getOffset())));
				}

			}
		} else {
			// Gold labels.

			if (!cutoffsDictionary.getRoles(words.get(dep.getHead()).word, dep.getCategory(), dep.getPreposition(),
					dep.getArgNumber()).contains(dep.getSemanticRole())) {
				throw new RuntimeException("Role " + dep.getSemanticRole() + " should have been pruned:"
						+ words.get(dep.getHead()).word + " " + dep.getCategory() + " " + dep.getPreposition() + " "
						+ dep.getArgNumber());
			}

			Preconditions.checkState(cutoffsDictionary.isFrequent(dep.getCategory(), dep.getArgNumber(),
					dep.getSemanticRole()));

			possibleLabels.add(new ConjunctiveDependencyNode(dep, parent, cutoffsDictionary.isFrequent(
					dep.getSemanticRole(), dep.getOffset())));
		}

		for (final ConjunctiveDependencyNode depNode : possibleLabels) {
			parent.addChild(depNode);
			conjunctiveNodes.add(depNode);
		}
		return parent;
	}

	private DisjunctiveNode parseDisjunctive(final Key key, final Map<Key, DisjunctiveNode> disjuctiveNodeCache,
			final Map<ResolvedDependency, DisjunctiveNode> dependencyNodeCache,
			final Map<Value, ConjunctiveNode> conjuctiveNodeCache) {
		DisjunctiveTreeNode result = (DisjunctiveTreeNode) disjuctiveNodeCache.get(key);

		if (result == null) {
			result = new DisjunctiveTreeNode(key.category, key.startIndex, key.lastIndex, key.ruleClass);

			for (final Value child : key.getChildren()) {
				result.addChild(parseConjunctive(child, disjuctiveNodeCache, conjuctiveNodeCache, dependencyNodeCache,
						result));
			}
			disjuctiveNodeCache.put(key, result);
		}

		return result;
	}

	static abstract class ConjunctiveNode {

		private final DisjunctiveNode parent;

		public List<DisjunctiveNode> getChildren() {
			return children;
		}

		public abstract int getEndIndex();

		public abstract int getStartIndex();

		public int getUnaryRuleID() {
			throw new UnsupportedOperationException();
		}

		private final List<DisjunctiveNode> children;

		private ConjunctiveNode(final DisjunctiveNode parent, final List<DisjunctiveNode> children) {
			this.children = children;
			this.parent = parent;
			for (final DisjunctiveNode child : children) {
				child.addParent(this);
			}

		}

		public abstract Category getCategory();

		public abstract ResolvedDependency getDependency();

		public abstract int getIndex();

		public DisjunctiveNode getParent() {
			return parent;
		}

		abstract void updateExpectations(List<InputWord> words, double nodeProbability, double[] result,
				FeatureSet featureSet, Map<FeatureKey, Integer> featureToIndexMap);

		abstract double getLogScore(List<InputWord> words, FeatureSet featureSet,
				Map<FeatureKey, Integer> featureToIndexMap, double[] featureWeights);

	}

	private static abstract class CachedConjunctiveNode extends ConjunctiveNode {

		private CachedConjunctiveNode(final DisjunctiveNode parent, final List<DisjunctiveNode> children) {
			super(parent, children);
		}

		private int[] featureIndicesCache = null;

		@Override
		final void updateExpectations(final List<InputWord> words, final double nodeScore, final double[] result,
				final FeatureSet featureSet, final Map<FeatureKey, Integer> featureToIndexMap) {
			for (final int featureIndex : getFeatureIndicesCached(words, featureSet, featureToIndexMap)) {
				result[featureIndex] += nodeScore;
			}
		}

		@Override
		final double getLogScore(final List<InputWord> words, final FeatureSet featureSet,
				final Map<FeatureKey, Integer> featureToIndexMap, final double[] featureWeights) {
			double result = 0.0;
			for (final int featureIndex : getFeatureIndicesCached(words, featureSet, featureToIndexMap)) {
				result += featureWeights[featureIndex];
			}

			return result;
		}

		private int[] getFeatureIndicesCached(final List<InputWord> words, final FeatureSet featureSet,
				final Map<FeatureKey, Integer> featureToIndexMap) {
			if (featureIndicesCache == null) {
				featureIndicesCache = Ints.toArray(getFeatureIndices(words, featureSet, featureToIndexMap));
			}
			return featureIndicesCache;
		}

		abstract Collection<Integer> getFeatureIndices(List<InputWord> words, FeatureSet featureSet,
				Map<FeatureKey, Integer> featureToIndexMap);
	}

	private static class ConjunctiveLexicalNode extends ConjunctiveNode {
		private final Category category;
		private final int index;
		private Double cachedValue = null;

		private ConjunctiveLexicalNode(final Category category, final int index, final DisjunctiveNode parent) {
			super(parent, Collections.emptyList());
			this.category = category;
			this.index = index;
		}

		@Override
		public Category getCategory() {
			return category;
		}

		@Override
		public ResolvedDependency getDependency() {
			return null; // throw new UnsupportedOperationException();
		}

		@Override
		public int getIndex() {
			return index;
		}

		// Supertagger output never changes (in the current model...), so we can cache it.
		// This also saves running the LSTM for each word in the sentence.
		private final static Map<List<InputWord>, List<Map<Category, Double>>> cache = Collections
				.synchronizedMap(new HashMap<>());

		private double getPretrainedValue(final List<InputWord> words, final FeatureSet featureSet) {
			if (cachedValue == null) {

				List<Map<Category, Double>> tags = cache.get(words);
				if (tags == null) {
					tags = featureSet.lexicalCategoryFeatures.getCategoryScores(words, 1.0);
					cache.put(words, tags);
				}

				cachedValue = tags.get(index).get(category);
			}

			return cachedValue;
		}

		@Override
		void updateExpectations(final List<InputWord> words, final double nodeScore, final double[] result,
				final FeatureSet featureSet, final Map<FeatureKey, Integer> featureToIndexMap) {

			final int featureIndex = featureToIndexMap.get(featureSet.lexicalCategoryFeatures.getDefault());
			result[featureIndex] += nodeScore * getPretrainedValue(words, featureSet);

		}

		@Override
		double getLogScore(final List<InputWord> words, final FeatureSet featureSet,
				final Map<FeatureKey, Integer> featureToIndexMap, final double[] featureWeights) {
			double result = 0.0;
			final int featureIndex = featureToIndexMap.get(featureSet.lexicalCategoryFeatures.getDefault());
			result += getPretrainedValue(words, featureSet) * featureWeights[featureIndex];

			return result;
		}

		@Override
		public int getEndIndex() {
			return index + 1;
		}

		@Override
		public int getStartIndex() {
			return index;
		}

	}

	private static class ConjunctiveUnaryNode extends CachedConjunctiveNode {
		private final int unaryRuleID;
		private final DisjunctiveTreeNode child;
		private final Category category;

		private ConjunctiveUnaryNode(final DisjunctiveTreeNode parent, final List<DisjunctiveNode> children,
				final int unaryRuleID) {
			// Can have multiple children if dependencies are resolved
			super(parent, children);
			this.unaryRuleID = unaryRuleID;
			this.child = (DisjunctiveTreeNode) children.get(0); // TODO
			this.category = parent.category;
		}

		@Override
		public Category getCategory() {
			return category;

		}

		@Override
		public int getUnaryRuleID() {
			return unaryRuleID;
		}

		@Override
		public ResolvedDependency getDependency() {
			return null;
		}

		@Override
		public int getIndex() {
			throw new UnsupportedOperationException();

		}

		@Override
		Collection<Integer> getFeatureIndices(final List<InputWord> words, final FeatureSet featureSet,
				final Map<FeatureKey, Integer> featureToIndexMap) {
			final Collection<Integer> result = new ArrayList<>(featureSet.unaryRuleFeatures.size());
			for (final UnaryRuleFeature feature : featureSet.unaryRuleFeatures) {
				final int index = feature.getFeatureIndex(getUnaryRuleID(), words, child.startIndex,
						child.lastIndex + 1, featureToIndexMap);
				result.add(index);
			}
			return result;
		}

		@Override
		public int getEndIndex() {
			return child.lastIndex + 1;

		}

		@Override
		public int getStartIndex() {
			return child.startIndex;
		}
	}

	private static class ConjunctiveBinaryNode extends CachedConjunctiveNode {

		private final DisjunctiveTreeNode left;
		private final DisjunctiveTreeNode right;
		private final DisjunctiveTreeNode parent;

		private ConjunctiveBinaryNode(final DisjunctiveTreeNode parent, final List<DisjunctiveNode> children) {
			super(parent, children);
			this.left = (DisjunctiveTreeNode) children.get(0); // TODO
			this.right = (DisjunctiveTreeNode) children.get(1); // TODO
			this.parent = parent;

		}

		@Override
		public Category getCategory() {
			return parent.category;
		}

		@Override
		public ResolvedDependency getDependency() {
			return null;
		}

		@Override
		public int getIndex() {
			throw new UnsupportedOperationException();
		}

		@Override
		Collection<Integer> getFeatureIndices(final List<InputWord> words, final FeatureSet featureSet,
				final Map<FeatureKey, Integer> featureToIndexMap) {

			final List<Integer> result = new ArrayList<>(featureSet.binaryFeatures.size());
			for (final BinaryFeature feature : featureSet.binaryFeatures) {
				final int index = feature.getFeatureIndex(parent.category, parent.ruleClass, left.category,
						left.ruleClass.getNormalFormClassForRule(), left.lastIndex - left.startIndex + 1,
						right.category, right.ruleClass.getNormalFormClassForRule(), right.lastIndex - right.startIndex
								+ 1, words, featureToIndexMap);
				result.add(index);
			}

			if (parent.lastIndex - parent.startIndex == words.size() - 1) {
				for (final RootCategoryFeature feature : featureSet.rootFeatures) {
					final int index = feature.getFeatureIndex(words, parent.category, featureToIndexMap);
					result.add(index);
				}
			}

			return result;
		}

		@Override
		public int getEndIndex() {
			return right.lastIndex + 1;

		}

		@Override
		public int getStartIndex() {
			return left.startIndex;
		}

	}

	private static class ConjunctiveDependencyNode extends CachedConjunctiveNode {
		private final ResolvedDependency dependency;
		private final boolean includeDepsFeatures;

		private ConjunctiveDependencyNode(final ResolvedDependency dep, final DisjunctiveNode parent,
				final boolean includeDepsFeatures) {
			super(parent, Collections.emptyList());
			this.dependency = dep;
			this.includeDepsFeatures = includeDepsFeatures;
		}

		@Override
		public Category getCategory() {
			return null;
		}

		@Override
		public ResolvedDependency getDependency() {
			return dependency;
		}

		@Override
		public int getIndex() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getStartIndex() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getEndIndex() {
			throw new UnsupportedOperationException();
		}

		@Override
		Collection<Integer> getFeatureIndices(final List<InputWord> words, final FeatureSet featureSet,
				final Map<FeatureKey, Integer> featureToIndexMap) {
			final Collection<Integer> result = new ArrayList<>();
			if (dependency.getSemanticRole() != SRLFrame.NONE && dependency.getOffset() != 0 && includeDepsFeatures) {
				for (final BilexicalFeature feature : featureSet.dependencyFeatures) {
					final int index = feature.getFeatureIndex(words, dependency.getSemanticRole(),
							dependency.getHead(), dependency.getArgumentIndex(), featureToIndexMap);
					result.add(index);
				}
			}

			for (final ArgumentSlotFeature feature : featureSet.argumentSlotFeatures) {
				final int index = feature.getFeatureIndex(words, dependency.getHead(),
						dependency.getSemanticRole(), dependency.getCategory(), dependency.getArgNumber(),
						dependency.getPreposition(), featureToIndexMap);
				result.add(index);
			}

			if (dependency.getPreposition() != Preposition.NONE) {
				for (final PrepositionFeature feature : featureSet.prepositionFeatures) {
					final int index = feature.getFeatureIndex(words, dependency.getHead(),
							dependency.getCategory(), dependency.getPreposition(), dependency.getArgNumber(),
							featureToIndexMap);
					result.add(index);
				}
			}

			return result;
		}
	}

	static class DisjunctiveTreeNode extends DisjunctiveNode {
		private final Category category;
		private final int startIndex;
		private final int lastIndex;
		private final RuleType ruleClass;

		public DisjunctiveTreeNode(final Category category, final int startIndex, final int lastIndex,
				final RuleType ruleClass) {
			super();
			this.category = category;
			this.startIndex = startIndex;
			this.lastIndex = lastIndex;
			this.ruleClass = ruleClass;
		}

		public Category getCategory() {
			return category;
		}
	}

	static class DisjunctiveNode {

		private final List<ConjunctiveNode> children = new ArrayList<>();
		private final List<ConjunctiveNode> parents = new ArrayList<>();

		public Collection<ConjunctiveNode> getChildren() {
			return children;
		}

		void addChild(final ConjunctiveNode child) {
			children.add(child);
		}

		private void addParent(final ConjunctiveNode parent) {
			parents.add(parent);
		}

		public Collection<ConjunctiveNode> getParents() {
			return parents;
		}

	}

	public Collection<DisjunctiveNode> getRoots() {
		return roots;
	}

	public Collection<ConjunctiveNode> getConjunctiveNodes() {
		return conjunctiveNodes;
	}
}
