package edu.uw.easysrl.syntax.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.carrotsearch.hppc.ObjectDoubleHashMap;
import com.google.common.collect.ImmutableList;

import edu.uw.easysrl.dependencies.DependencyStructure.UnlabelledDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.model.feature.FeatureCache;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.model.feature.PrepositionFeature;
import edu.uw.easysrl.util.Util.Scored;

/**
 * Represents a distribution over extended lexical entries.
 *
 */
class ExtendedLexicalEntry {
	private final int wordIndex;
	private final List<InputWord> sentence;
	private final Forest forest;
	private final FeatureSet featureSet;
	private final ObjectDoubleHashMap<FeatureKey> featureToScore;

	private final Double[] viterbiScoreConjunctiveCache;
	private final Double[] featureScoreCache;
	private final FeatureCache featureCache;

	ExtendedLexicalEntry(final FeatureSet featureSet, final int wordIndex, final List<InputWord> words,
			final Forest forest, final ObjectDoubleHashMap<FeatureKey> featureToScore, final FeatureCache featureCache) {
		this.wordIndex = wordIndex;
		this.sentence = words;

		this.forest = forest;
		this.featureSet = featureSet;
		this.featureToScore = featureToScore;
		this.viterbiScoreConjunctiveCache = new Double[forest.numberOfConjunctiveNodes];
		this.featureScoreCache = new Double[forest.numberOfConjunctiveNodes];
		this.featureCache = featureCache;

	}

	private final double logViterbiScoreDisjunctive(final DisjunctiveNode disjunctiveNode) {

		if (disjunctiveNode == null) {
			return 0.0;
		}

		Double result = viterbiScoreConjunctiveCache[disjunctiveNode.id];
		if (result == null) {
			assert (!disjunctiveNode.getChildren().isEmpty());

			result = Double.NEGATIVE_INFINITY;

			for (final ConjunctiveNode conjunctiveNode : disjunctiveNode.getChildren()) {
				if (conjunctiveNode.isValid(wordIndex, sentence.size())) {
					result = Math.max(result, logViterbiScoreConjunctive(conjunctiveNode));
				}
			}

			viterbiScoreConjunctiveCache[disjunctiveNode.id] = result;
		}

		return result;
	}

	private final double logViterbiScoreConjunctive(final ConjunctiveNode conjunctiveNode) {
		Double result = viterbiScoreConjunctiveCache[conjunctiveNode.id];
		if (result == null) {

			final Collection<DisjunctiveNode> children = conjunctiveNode.getChildren();
			result = logScoreOfFeaturesAtNode(conjunctiveNode);

			for (final DisjunctiveNode daughter : children) {
				result = result + logViterbiScoreDisjunctive(daughter);
			}

			viterbiScoreConjunctiveCache[conjunctiveNode.id] = result;
		}

		return result;
	}

	private double logScoreOfFeaturesAtNode(final ConjunctiveNode node) {
		Double result = featureScoreCache[node.id];
		if (result == null) {
			result = node.getLogScore(sentence, wordIndex, featureSet, featureToScore, featureCache);
			featureScoreCache[node.id] = result;
		}

		return result;

	}

	static Forest makeUnlexicalizedForest(final String word, final Collection<Category> allLexicalCategories,
			final int maxDependencyLength, final CutoffsDictionary cutoffsDictionary, final boolean includeSlotNodes,
			final boolean includeAttachmentNodes) {
		final AtomicInteger numberOfConjunctiveNodes = new AtomicInteger();
		final Map<Object, DisjunctiveNode> cache = new HashMap<>();
		final DisjunctiveNode rootDisjunctive = new DisjunctiveNode(numberOfConjunctiveNodes.getAndIncrement());
		final Collection<ConjunctiveDependencyNode> dependencyNodes = new ArrayList<>();
		final Collection<ConjunctiveCategoryNode> categoryNodes = new ArrayList<>();
		final Collection<ConjunctiveArgumentSlotNode> argumentSlotNodes = new ArrayList<>();
		final Collection<ConjunctivePrepositionNode> prepositionNodes = new ArrayList<>();

		Collection<Category> categoriesForWord = cutoffsDictionary.getCategoriesForWord(word);
		if (categoriesForWord.size() == 0) {
			categoriesForWord = allLexicalCategories;
		}
		for (final Category category : categoriesForWord) {
			makeCategoryNode(word, category, rootDisjunctive, cache, maxDependencyLength, categoryNodes,
					dependencyNodes, argumentSlotNodes, prepositionNodes, cutoffsDictionary, includeSlotNodes,
					includeAttachmentNodes, numberOfConjunctiveNodes);
		}

		return new Forest(maxDependencyLength, rootDisjunctive, categoryNodes, dependencyNodes, argumentSlotNodes,
				numberOfConjunctiveNodes.get());

	}

	static class Forest {
		private final DisjunctiveNode root;
		private final Collection<ConjunctiveCategoryNode> categoryNodes;
		private final Collection<ConjunctiveDependencyNode> dependencyNodes;
		private final Collection<ConjunctiveArgumentSlotNode> argumentSlotNodes;
		private final int maxDependencyLength;

		private final int numberOfConjunctiveNodes;

		private final ConjunctiveCategoryNode[] categoryToNode;
		private final ConjunctiveDependencyNode[][] offsetToSRLtoNode;

		private final int minOffset;

		private Forest(final int maxDependencyLength, final DisjunctiveNode root,
				final Collection<ConjunctiveCategoryNode> categoryNodes,
				final Collection<ConjunctiveDependencyNode> dependencyNodes,
				final Collection<ConjunctiveArgumentSlotNode> argumentSlotNodes, final int numberOfConjunctiveNodes) {
			super();
			this.maxDependencyLength = maxDependencyLength;
			this.root = root;
			this.categoryNodes = ImmutableList.copyOf(categoryNodes);
			this.dependencyNodes = ImmutableList.copyOf(dependencyNodes);
			this.argumentSlotNodes = ImmutableList.copyOf(argumentSlotNodes);

			this.numberOfConjunctiveNodes = numberOfConjunctiveNodes;

			int maxArguments = 0;
			int maxOffset = 0;
			int minOffset = 0;
			int maxCategoryIndex = 0;
			for (final ConjunctiveCategoryNode categoryNode : categoryNodes) {
				maxArguments = Math.max(maxArguments, categoryNode.category.getNumberOfArguments());
				maxCategoryIndex = Math.max(maxCategoryIndex, categoryNode.category.getID());
			}

			for (final ConjunctiveDependencyNode dependencyNode : dependencyNodes) {
				maxOffset = Math.max(maxOffset, dependencyNode.offset);
				minOffset = Math.min(minOffset, dependencyNode.offset);
			}

			this.minOffset = minOffset;

			categoryToNode = new ConjunctiveCategoryNode[maxCategoryIndex + 1];

			offsetToSRLtoNode = new ConjunctiveDependencyNode[maxOffset - minOffset + 1][SRLLabel.numberOfLabels() + 1];

			for (final ConjunctiveCategoryNode categoryNode : categoryNodes) {
				categoryToNode[categoryNode.category.getID()] = categoryNode;
			}

			for (final ConjunctiveDependencyNode dependencyNode : dependencyNodes) {
				offsetToSRLtoNode[dependencyNode.offset - minOffset][dependencyNode.label.getID()] = dependencyNode;

			}

		}

		public Collection<ConjunctiveCategoryNode> getCategoryNodes() {
			return categoryNodes;
		}

		public Collection<ConjunctiveDependencyNode> getDependencyNodes() {
			return dependencyNodes;
		}

		public Collection<ConjunctiveArgumentSlotNode> getArgumentSlotNodes() {
			return argumentSlotNodes;
		}

		private DisjunctiveNode getNode(final Category category, final int argNumber) {
			final ConjunctiveCategoryNode node = getNode(category);
			if (argNumber > node.argNumberToChild.length) {
				return null;
			}
			return node.argNumberToChild[argNumber - 1];
		}

		DisjunctiveNode getNode(final Category category, final int argNumber, final Preposition preposition) {
			final DisjunctiveNode slotNode = getNode(category, argNumber);
			if (slotNode != null) {

				final DisjunctiveNode roleDisjunction = slotNode.getChild(preposition);

				return roleDisjunction;
			}

			return null;
		}

		private ConjunctiveCategoryNode getNode(final Category category) {
			return categoryToNode[category.getID()];
		}

		private ConjunctiveNode getNode(final int offset, final SRLLabel semanticRole) {
			return offsetToSRLtoNode[offset - minOffset][semanticRole.getID()];
		}

	}

	private static DisjunctiveNode makePrepositionNode(final String word, final Category category,
			final int argumentNumber, final Map<Object, DisjunctiveNode> cache, final int maxDependencyLength,
			final Collection<ConjunctiveDependencyNode> dependencyNodes,
			final Collection<ConjunctiveArgumentSlotNode> argumentSlotNodes,
			final Collection<ConjunctivePrepositionNode> prepositionNodes, final CutoffsDictionary cutoffsDictionary,
			final boolean includeAttachmentNodes, final AtomicInteger numberOfConjunctiveNodes) {

		if (category.getArgument(argumentNumber).equals(Category.PP)) {
			final DisjunctiveNode result = new DisjunctivePPNode(numberOfConjunctiveNodes.getAndIncrement());

			for (final Preposition preposition : Preposition.values()) {
				if (preposition == Preposition.NONE) {
					// continue;
				}

				// Prepositions for this argument, e.g. S\NP/PP.2
				// Each has a single child, which is a disjunction over possible
				// labels

				final DisjunctiveNode argumentSlotNode = makeArgumentSlotNode(word, category, argumentNumber,
						preposition, cache, maxDependencyLength, dependencyNodes, argumentSlotNodes, cutoffsDictionary,
						includeAttachmentNodes, numberOfConjunctiveNodes);

				// if (argumentSlotNode.children.size() > 0) {
				// Only modelling prepositions for arguments that may be
				// SRLs.
				prepositionNodes.add(new ConjunctivePrepositionNode(category, argumentNumber, preposition, result,
						Arrays.asList(argumentSlotNode), numberOfConjunctiveNodes.getAndIncrement()));
				// }
			}

			return result;

		} else {
			return makeArgumentSlotNode(word, category, argumentNumber, Preposition.NONE, cache, maxDependencyLength,
					dependencyNodes, argumentSlotNodes, cutoffsDictionary, includeAttachmentNodes,
					numberOfConjunctiveNodes);
		}
	}

	private static DisjunctiveNode makeArgumentSlotNode(final String word, final Category category,
			final int argumentNumber, final Preposition preposition, final Map<Object, DisjunctiveNode> cache,
			final int maxDependencyLength, final Collection<ConjunctiveDependencyNode> dependencyNodes,
			final Collection<ConjunctiveArgumentSlotNode> argumentSlotNodes, final CutoffsDictionary cutoffsDictionary,
			final boolean includeAttachmentNodes, final AtomicInteger numberOfConjunctiveNodes) {

		final DisjunctiveNode result = new DisjunctiveNode(numberOfConjunctiveNodes.getAndIncrement());

		for (final SRLLabel label : cutoffsDictionary.getRoles(word, category, preposition, argumentNumber)// SRLFrame.getAllSrlLabels()
		) {

			List<DisjunctiveNode> attachmentNode;
			if (label == SRLFrame.NONE) {
				// Have a node, but no children.
				attachmentNode = Collections.emptyList();
			} else if ((cutoffsDictionary != null && !cutoffsDictionary.isFrequent(category, argumentNumber, label))) {
				// No node.
				continue;
			} else if (!includeAttachmentNodes) {
				// Have a node, but no children.
				attachmentNode = Collections.emptyList();
			} else {
				attachmentNode = Arrays.asList(makeAttachmentNode(label, cache, maxDependencyLength, dependencyNodes,
						cutoffsDictionary, numberOfConjunctiveNodes));
			}
			argumentSlotNodes.add(new ConjunctiveArgumentSlotNode(category, label, argumentNumber, preposition, result,
					attachmentNode, numberOfConjunctiveNodes.getAndIncrement()));
		}

		return result;
	}

	private static ConjunctiveCategoryNode makeCategoryNode(final String word, final Category category,
			final DisjunctiveNode parent, final Map<Object, DisjunctiveNode> cache, final int maxDependencyLength,
			final Collection<ConjunctiveCategoryNode> categoryNodes,
			final Collection<ConjunctiveDependencyNode> dependencyNodes,
			final Collection<ConjunctiveArgumentSlotNode> argumentSlotNodes,
			final Collection<ConjunctivePrepositionNode> prepositionNodes, final CutoffsDictionary cutoffsDictionary,
			final boolean includeSlotNodes, final boolean includeAttachmentNodes,
			final AtomicInteger numberOfConjunctiveNodes) {

		final List<DisjunctiveNode> children = new ArrayList<>();

		final DisjunctiveNode[] argNumberToChild = new DisjunctiveNode[category.getNumberOfArguments()];

		if (includeSlotNodes) {
			for (int argumentNUmber = 1; argumentNUmber <= category.getNumberOfArguments(); argumentNUmber++) {

				if (!cutoffsDictionary.isFrequentWithAnySRLLabel(category, argumentNUmber)) {
					continue;
				}

				final DisjunctiveNode srlsForArgument = makePrepositionNode(word, category, argumentNUmber, cache,
						maxDependencyLength, dependencyNodes, argumentSlotNodes, prepositionNodes, cutoffsDictionary,
						includeAttachmentNodes, numberOfConjunctiveNodes);
				if (srlsForArgument.children.size() > 0) {
					children.add(srlsForArgument);
					argNumberToChild[argumentNUmber - 1] = srlsForArgument;
				}

			}
		}

		final ConjunctiveCategoryNode result = new ConjunctiveCategoryNode(category, parent, children,
				numberOfConjunctiveNodes.getAndIncrement(), argNumberToChild);

		categoryNodes.add(result);

		return result;
	}

	private static DisjunctiveNode makeAttachmentNode(final SRLLabel label, final Map<Object, DisjunctiveNode> cache,
			final int maxDependencyLength, final Collection<ConjunctiveDependencyNode> dependencyNodes,
			final CutoffsDictionary cutoffsDictionary, final AtomicInteger numberOfConjunctiveNodes) {
		final Object key = label;
		DisjunctiveNode result = cache.get(key);

		if (result == null) {
			result = new DisjunctiveNode(numberOfConjunctiveNodes.getAndIncrement());
			for (int offset = -maxDependencyLength; offset <= maxDependencyLength; offset++) {

				if (cutoffsDictionary != null && !cutoffsDictionary.isFrequent(label, offset)) {
					continue;
				}

				final ConjunctiveDependencyNode dependencyChild = new ConjunctiveDependencyNode(label, offset, result,
						Collections.emptyList(), numberOfConjunctiveNodes.getAndIncrement());
				dependencyNodes.add(dependencyChild);
			}

			cache.put(key, result);
		}

		return result;
	}

	private static class DisjunctiveNode {

		private final Collection<ConjunctiveNode> children = new ArrayList<>();
		private final Collection<ConjunctiveNode> parents = new ArrayList<>();
		private final int id;

		private DisjunctiveNode(final int id) {
			this.id = id;
		}

		public Collection<ConjunctiveNode> getChildren() {
			return children;
		}

		void addChild(final ConjunctiveNode child, @SuppressWarnings("unused") final Preposition prep) {
			children.add(child);
		}

		private void addParent(final ConjunctiveNode parent) {
			parents.add(parent);
		}

		public Collection<ConjunctiveNode> getParents() {
			return parents;
		}

		/**
		 * Weird function that returns the correct child for preposition nodes, and otherwise returns itself.
		 */
		DisjunctiveNode getChild(@SuppressWarnings("unused") final Preposition prep) {
			return this;
		}

	}

	private static class DisjunctivePPNode extends DisjunctiveNode {
		private final DisjunctiveNode[] prepToChild;

		private DisjunctivePPNode(final int id) {
			super(id);
			this.prepToChild = new DisjunctiveNode[Preposition.numberOfPrepositions()];
		}

		@Override
		DisjunctiveNode getChild(final Preposition prep) {

			return prepToChild[prep.getID()];
		}

		@Override
		void addChild(final ConjunctiveNode child, final Preposition prep) {
			prepToChild[prep.getID()] = child.getChildren().get(0);
			super.addChild(child, prep);
		}
	}

	private static abstract class ConjunctiveNode {

		public List<DisjunctiveNode> getChildren() {
			return children;
		}

		public boolean isValid(@SuppressWarnings("unused") final int wordIndex,
				@SuppressWarnings("unused") final int sentenceLength) {
			return true;
		}

		abstract double getLogScore(List<InputWord> words, int wordIndex, FeatureSet featureSet,
				ObjectDoubleHashMap<FeatureKey> featureToScore, FeatureCache featureCache);

		private final List<DisjunctiveNode> children;
		private final DisjunctiveNode parent;
		private final int id;

		private ConjunctiveNode(final DisjunctiveNode parent, final List<DisjunctiveNode> children, final int id,
				final Preposition prep) {
			this.parent = parent;
			this.children = children;
			this.id = id;

			parent.addChild(this, prep);
			for (final DisjunctiveNode child : children) {
				child.addParent(this);
			}
		}

		public DisjunctiveNode getParent() {
			return parent;
		}

	}

	static class ConjunctiveCategoryNode extends ConjunctiveNode {
		private final DisjunctiveNode[] argNumberToChild;

		private ConjunctiveCategoryNode(final Category category, final DisjunctiveNode parent,
				final List<DisjunctiveNode> children, final int id, final DisjunctiveNode[] argNumberToChild) {
			super(parent, children, id, null);
			this.category = category;
			this.argNumberToChild = argNumberToChild;
		}

		public Category getCategory() {
			return category;
		}

		private final Category category;

		@Override
		double getLogScore(final List<InputWord> words, final int wordIndex, final FeatureSet featureSet,
				final ObjectDoubleHashMap<FeatureKey> featureToScore, final FeatureCache featureCache) {

			return featureCache.getScore(wordIndex, category);

		}

	}

	private static class ConjunctiveDependencyNode extends ConjunctiveNode {
		private final SRLLabel label;
		private final int offset;

		private ConjunctiveDependencyNode(final SRLLabel label, final int offset, final DisjunctiveNode parent,
				final List<DisjunctiveNode> children, final int id) {
			super(parent, children, id, null);
			this.label = label;
			this.offset = offset;
		}

		@Override
		public boolean isValid(final int wordIndex, final int sentenceLength) {
			final int index = wordIndex + offset;
			return (index >= 0 && index < sentenceLength);
		}

		@Override
		double getLogScore(final List<InputWord> words, final int functorIndex, final FeatureSet featureSet,
				final ObjectDoubleHashMap<FeatureKey> featureToScore, final FeatureCache featureCache) {

			if (offset == 0) {
				// null attachment
				return 0;
			}

			final int argumentIndex = functorIndex + offset;

			if (argumentIndex < 0 || argumentIndex >= words.size()) {
				throw new RuntimeException();
			}

			return featureCache.getScore(functorIndex, label, argumentIndex);
		}

	}

	private static class ConjunctiveArgumentSlotNode extends ConjunctiveNode {
		private final int argumentNumber;
		private final SRLLabel label;
		private final Category category;
		private final Preposition preposition;

		private ConjunctiveArgumentSlotNode(final Category category, final SRLLabel label, final int argumentNumber,
				final Preposition preposition, final DisjunctiveNode parent, final List<DisjunctiveNode> children,
				final int id) {
			super(parent, children, id, null);
			this.category = category;
			this.label = label;
			this.argumentNumber = argumentNumber;
			this.preposition = preposition;
		}

		@Override
		double getLogScore(final List<InputWord> words, final int wordIndex, final FeatureSet featureSet,
				final ObjectDoubleHashMap<FeatureKey> featureToScore, final FeatureCache featureCache) {

			return featureCache.getScore(words, wordIndex, category, preposition, argumentNumber, label);
		}

		public SRLLabel getLabel() {
			return label;
		}
	}

	private static class ConjunctivePrepositionNode extends ConjunctiveNode {
		private final int argumentNumber;
		private final Category category;
		private final Preposition preposition;

		private ConjunctivePrepositionNode(final Category category, final int argumentNumber,
				final Preposition preposition, final DisjunctiveNode parent, final List<DisjunctiveNode> children,
				final int id) {
			super(parent, children, id, preposition);
			this.category = category;
			this.argumentNumber = argumentNumber;
			this.preposition = preposition;
		}

		@Override
		double getLogScore(final List<InputWord> words, final int wordIndex, final FeatureSet featureSet,
				final ObjectDoubleHashMap<FeatureKey> featureToScore, final FeatureCache featureCache) {
			if (preposition == Preposition.NONE) {
				return 0.0;
			}

			double scoreOfFeaturesAtNode = 0;
			for (final PrepositionFeature feature : featureSet.prepositionFeatures) {
				scoreOfFeaturesAtNode += feature.getFeatureScore(words, wordIndex, preposition, category,
						argumentNumber, featureToScore);
			}

			return scoreOfFeaturesAtNode;
		}
	}

	public Collection<ConjunctiveCategoryNode> getCategoryNodes() {
		return forest.getCategoryNodes();
	}

	double scoreNode(final ConjunctiveNode node) {
		return logScoreOfFeaturesAtNode(node);
	}

	double getLogUnnormalizedViterbiScore(final Category category) {
		return logViterbiScoreConjunctive(forest.getNode(category));
	}

	public double getLogUnnormalizedViterbiScore() {
		return logViterbiScoreDisjunctive(forest.root);
	}

	public double getLogUnnormalizedViterbiScore(final Collection<Category> unprunedCategories) {
		double result = Double.NEGATIVE_INFINITY;
		for (final Category cat : unprunedCategories) {
			result = Math.max(result, getLogUnnormalizedViterbiScore(cat));
		}
		return result;
	}

	double getLogUnnormalizedViterbiScore(final Category category, final int argNumber) {
		return // Math.max(0,
				logViterbiScoreDisjunctive(forest.getNode(category, argNumber));
	}

	private double getLogScoreOfFeaturesAtNode(final Category category, final int argNumber,
			final Preposition preposition) {

		if (preposition == Preposition.NONE) {
			return 0.0;
		}
		final DisjunctiveNode slotNode = forest.getNode(category, argNumber);
		if (slotNode != null) {
			final DisjunctiveNode roleDisjunction = slotNode.getChild(preposition);

			if (roleDisjunction != null) {
				return scoreNode(roleDisjunction.getParents().iterator().next());

			}
		}

		return 0.0;

	}

	private ConjunctiveNode getNode(final int offset, final SRLLabel label) {
		return forest.getNode(offset, label);
	}

	/**
	 * Finds the best label for a dependency.
	 */
	Scored<SRLLabel> getBestLabel(final UnlabelledDependency dep) {

		final DisjunctiveNode roleChoice = forest.getNode(dep.getCategory(), dep.getArgNumber(), dep.getPreposition());

		final double prepScore = getLogScoreOfFeaturesAtNode(dep.getCategory(), dep.getArgNumber(),
				dep.getPreposition());

		SRLLabel bestLabel = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		if (roleChoice == null// || dep.getOffset() == 0
		) {
			// Slots that are never SRL.
			return new Scored<>(SRLFrame.NONE, 0.0);
		}

		for (final ConjunctiveNode child : roleChoice.children) {
			final ConjunctiveArgumentSlotNode slotNode = (ConjunctiveArgumentSlotNode) child;
			double score = Double.NEGATIVE_INFINITY;

			if (slotNode.getLabel() == SRLFrame.NONE || dep.getOffset() == 0) {
				score = prepScore + scoreNode(slotNode);
			} else if (Math.abs(dep.getOffset()) < forest.maxDependencyLength
					&& dep.getOffset() - forest.minOffset < forest.offsetToSRLtoNode.length
					&& dep.getOffset() - forest.minOffset >= 0) {

				final ConjunctiveNode depNode = getNode(dep.getOffset(), slotNode.getLabel());
				score = prepScore + scoreNode(slotNode);

				if (depNode != null) {
					score += scoreNode(depNode);
				}
			}

			if (score > bestScore) {
				bestScore = score;
				bestLabel = slotNode.getLabel();
			}

		}

		return new Scored<>(bestLabel, bestScore);
	}
}
