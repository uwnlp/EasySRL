package edu.uw.easysrl.syntax.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.carrotsearch.hppc.ObjectDoubleHashMap;

import edu.uw.easysrl.dependencies.DependencyStructure.UnlabelledDependency;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLabelling;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.model.ExtendedLexicalEntry.ConjunctiveCategoryNode;
import edu.uw.easysrl.syntax.model.ExtendedLexicalEntry.Forest;
import edu.uw.easysrl.syntax.model.feature.Feature.BinaryFeature;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.model.feature.Feature.RootCategoryFeature;
import edu.uw.easysrl.syntax.model.feature.Feature.UnaryRuleFeature;
import edu.uw.easysrl.syntax.model.feature.FeatureCache;
import edu.uw.easysrl.syntax.model.feature.FeatureCache.SlotFeatureCache;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.util.Util.Scored;

public class SRLFactoredModel extends Model {
	private final double supertaggerBeam;
	private final List<ExtendedLexicalEntry> forests;
	private final Collection<UnaryRuleFeature> unaryRuleFeatures;

	private final List<List<Scored<ConjunctiveCategoryNode>>> categoriesForWord;

	private SRLFactoredModel(final List<ExtendedLexicalEntry> forests, final double supertaggerBeam,
			final Collection<UnaryRuleFeature> unaryRuleFeatures, final ObjectDoubleHashMap<FeatureKey> featureToScore,
			final Collection<BinaryFeature> binaryFeatures, final Collection<RootCategoryFeature> rootFeatures,
			final List<InputWord> sentence

			) {
		super(forests.size());
		this.forests = forests;
		this.supertaggerBeam = supertaggerBeam;
		this.unaryRuleFeatures = unaryRuleFeatures;

		this.featureToScore = featureToScore;
		this.binaryFeatures = binaryFeatures;
		this.rootFeatures = rootFeatures;
		this.sentence = sentence;

		this.categoriesForWord = getCategoriesForWords(sentence);
		double globalUpperBound = 0.0;
		upperBoundsForWord = new ArrayList<>(forests.size());
		for (int i = 0; i < sentence.size(); i++) {
			double upperBoundForWord = Double.NEGATIVE_INFINITY;
			for (final Scored<ConjunctiveCategoryNode> cat : categoriesForWord.get(i)) {
				upperBoundForWord = Math.max(upperBoundForWord,
						forests.get(i).getLogUnnormalizedViterbiScore(cat.getObject().getCategory()));
			}

			upperBoundsForWord.add(upperBoundForWord);
			globalUpperBound += upperBoundForWord;
		}

		this.globalUpperBound = globalUpperBound;
	}

	private List<List<Scored<ConjunctiveCategoryNode>>> getCategoriesForWords(final List<InputWord> words) {
		final List<List<Scored<ConjunctiveCategoryNode>>> categoriesForWord = new ArrayList<>(words.size());
		for (int i = 0; i < words.size(); i++) {
			final ExtendedLexicalEntry forest = forests.get(i);

			List<Scored<ConjunctiveCategoryNode>> scoredNodes = new ArrayList<>();
			for (final ConjunctiveCategoryNode categoryNode : forest.getCategoryNodes()) {
				final double insideScore = forest.scoreNode(categoryNode);
				scoredNodes.add(new Scored<>(categoryNode, insideScore));
			}
			Collections.sort(scoredNodes);
			if (scoredNodes.size() > 50) {
				scoredNodes = scoredNodes.subList(0, 50);
			}
			final double threshold = supertaggerBeam * Math.exp(scoredNodes.get(0).getScore());

			int numCats = 0;
			for (final Scored<ConjunctiveCategoryNode> node : scoredNodes) {

				if (Math.exp(node.getScore()) < threshold) {
					break;
				}

				numCats++;

			}
			categoriesForWord.add(scoredNodes.subList(0, numCats));

		}

		return categoriesForWord;
	}

	@Override
	public void buildAgenda(final PriorityQueue<AgendaItem> queue, final List<InputWord> words) {

		for (int i = 0; i < words.size(); i++) {
			final InputWord word = words.get(i);
			final ExtendedLexicalEntry forest = forests.get(i);

			final double outsideScoreUpperBound = globalUpperBound - upperBoundsForWord.get(i);

			for (final Scored<ConjunctiveCategoryNode> node : categoriesForWord.get(i)) {
				final Category category = node.getObject().getCategory();
				final double dependenciesUpperBound = getInsideDependenciesUpperBound(forest, category, node.getScore());

				queue.add(new AgendaItem(new SyntaxTreeNodeLeaf(word.word, word.pos, word.ner, category, i), node
						.getScore(), dependenciesUpperBound + outsideScoreUpperBound, i, 1, true));
			}
		}

	}

	@Override
	double getUpperBoundForWord(final int index) {
		return forests.get(index).getLogUnnormalizedViterbiScore();
	}

	private final double globalUpperBound;
	private final List<Double> upperBoundsForWord;
	private final ObjectDoubleHashMap<FeatureKey> featureToScore;
	private final Collection<BinaryFeature> binaryFeatures;
	private final Collection<RootCategoryFeature> rootFeatures;
	private final List<InputWord> sentence;

	@Override
	public AgendaItem combineNodes(final AgendaItem leftChild, final AgendaItem rightChild, final SyntaxTreeNode node) {

		double binaryRuleScore = 0.0;
		for (final BinaryFeature feature : binaryFeatures) {
			binaryRuleScore += feature.getFeatureScore(node.getCategory(), node.getRuleType(), leftChild.getParse()
					.getCategory(), leftChild.getParse().getRuleType().getNormalFormClassForRule(), leftChild
					.getSpanLength(), rightChild.getParse().getCategory(), rightChild.getParse().getRuleType()
					.getNormalFormClassForRule(), rightChild.getSpanLength(), null, featureToScore);
		}

		final int length = leftChild.spanLength + rightChild.spanLength;

		double rootScore = 0.0;
		if (length == forests.size()) {
			for (final RootCategoryFeature feature : rootFeatures) {
				rootScore += feature.getFeatureScore(sentence, node.getCategory(), featureToScore);
			}
		}

		final double newInsideScore = leftChild.getInsideScore() + rightChild.getInsideScore() + binaryRuleScore
				+ rootScore;

		final AgendaItem result = new AgendaItem(node, newInsideScore, leftChild.outsideScoreUpperbound
				+ rightChild.outsideScoreUpperbound - globalUpperBound, leftChild.getStartOfSpan(), length, true);

		return labelDependencies(result, node);
	}

	/**
	 * Creates a new AgendaItem by labelling the dependencies in the old one
	 */
	private AgendaItem labelDependencies(AgendaItem result, final SyntaxTreeNode node) {

		final List<UnlabelledDependency> resolvedUnlabelledDependencies = node.getResolvedUnlabelledDependencies();
		int i = 0;
		for (final UnlabelledDependency dep : resolvedUnlabelledDependencies) {

			final ExtendedLexicalEntry forest = forests.get(dep.getPredicateIndex());
			final Scored<SRLLabel> scoredLabel = forest.getBestLabels(dep);

			final double newInsideScore = result.getInsideScore() + scoredLabel.getScore();

			final SyntaxTreeNode labelling = new SyntaxTreeNodeLabelling(result.getParse(), dep.setLabel(scoredLabel
					.getObject()), resolvedUnlabelledDependencies.subList(i + 1, resolvedUnlabelledDependencies.size()));

			result = new AgendaItem(labelling, newInsideScore, result.outsideScoreUpperbound
					- forest.getLogUnnormalizedViterbiScore(dep.getCategory(), dep.getArgNumber()),
					result.getStartOfSpan(), result.getSpanLength(), true);
			i++;

		}

		return result;
	}

	double getInsideDependenciesUpperBound(final ExtendedLexicalEntry forest, final Category category,
			final double insideScore) {
		final double dependenciesUpperBound = forest.getLogUnnormalizedViterbiScore(category) - insideScore;

		return dependenciesUpperBound;
	}

	@Override
	public AgendaItem unary(final AgendaItem child, final SyntaxTreeNode result, final UnaryRule rule) {

		double insideScore = child.getInsideScore();
		for (final UnaryRuleFeature feature : unaryRuleFeatures) {
			insideScore += feature.getFeatureScore(rule.getID(), sentence, child.startOfSpan, child.startOfSpan
					+ child.spanLength, featureToScore);
		}
		// + unaryRuleScores[rule.getID()];

		AgendaItem agendaItem = new AgendaItem(result, insideScore, child.outsideScoreUpperbound, child.startOfSpan,
				child.spanLength, true);

		agendaItem = labelDependencies(agendaItem, agendaItem.parse);

		return agendaItem;
	}

	// Worst class name EVER.
	public static class SRLFactoredModelFactory extends ModelFactory {
		private final CutoffsDictionary cutoffsDictionary;
		private final FeatureSet featureSet;
		private final Collection<Category> lexicalCategories;
		private final boolean usingDependencyFeatures;
		private final boolean usingSlotFeatures;
		private final double supertaggerBeam;
		private final SlotFeatureCache slotFeatureCache;
		private final double supertaggingFeatureScore;
		private final ObjectDoubleHashMap<FeatureKey> featureToScore;

		public SRLFactoredModelFactory(final double[] weights, final FeatureSet featureSet,
				final Collection<Category> lexicalCategories, final CutoffsDictionary cutoffs,
				final Map<FeatureKey, Integer> featureToIndex, final double supertaggerBeam) {
			this.featureSet = featureSet;
			this.cutoffsDictionary = cutoffs;
			this.lexicalCategories = lexicalCategories;
			this.usingDependencyFeatures = !featureSet.dependencyFeatures.isEmpty();
			this.usingSlotFeatures = !featureSet.argumentSlotFeatures.isEmpty();
			this.supertaggerBeam = supertaggerBeam;

			featureToScore = new ObjectDoubleHashMap<>(featureToIndex.size(), 0.1);

			for (final java.util.Map.Entry<FeatureKey, Integer> entry : featureToIndex.entrySet()) {
				featureToScore.put(entry.getKey(), weights[entry.getValue()]);
			}

			final FeatureKey supertaggingFeatureKey = featureSet.lexicalCategoryFeatures.getDefault();
			supertaggingFeatureScore = weights[featureToIndex.get(supertaggingFeatureKey)];
			featureToScore.put(supertaggingFeatureKey, supertaggingFeatureScore);

			this.slotFeatureCache = new SlotFeatureCache(featureSet, featureToScore);

		}

		@Override
		public Model make(final List<InputWord> sentence) {
			final FeatureCache featureCache = new FeatureCache(sentence, featureToScore, featureSet,
					supertaggingFeatureScore, slotFeatureCache);

			final List<ExtendedLexicalEntry> forests = new ArrayList<>(sentence.size());
			int wordIndex = 0;
			for (final InputWord word : sentence) {
				final Forest forest = ExtendedLexicalEntry.makeUnlexicalizedForest(word.word, lexicalCategories, 50,
						cutoffsDictionary, usingSlotFeatures, usingDependencyFeatures);

				forests.add(new ExtendedLexicalEntry(featureSet, wordIndex, sentence, forest, featureToScore,
						featureCache));

				wordIndex++;
			}

			return new SRLFactoredModel(forests, supertaggerBeam, featureSet.unaryRuleFeatures, featureToScore,
					featureSet.binaryFeatures, featureSet.rootFeatures, sentence);
		}

		@Override
		public Collection<Category> getLexicalCategories() {
			return lexicalCategories;
		}

	}
}
