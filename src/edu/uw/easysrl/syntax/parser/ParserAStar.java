package edu.uw.easysrl.syntax.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.NormalForm;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.syntax.parser.ChartCell.Cell1Best;
import edu.uw.easysrl.syntax.parser.ChartCell.Cell1BestTreeBased;
import edu.uw.easysrl.syntax.parser.ChartCell.ChartCellFactory;
import edu.uw.easysrl.syntax.parser.ChartCell.ChartCellNbestFactory;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util.Scored;

public class ParserAStar extends AbstractParser {

	private final ModelFactory modelFactory;

	private final int maxChartSize;
	private final ChartCellFactory cellFactory;
	private final boolean usingDependencies;

	public ParserAStar(final ModelFactory modelFactory, final int maxSentenceLength, final int nbest,
			final List<Category> validRootCategories, final File modelFolder, final int maxChartSize)
			throws IOException {
		super(TaggerEmbeddings.loadCategories(new File(modelFolder, "categories")), maxSentenceLength, nbest,
				validRootCategories, modelFolder);
		this.modelFactory = modelFactory;
		this.maxChartSize = maxChartSize;
		this.usingDependencies = modelFactory.isUsingDependencies();
		this.cellFactory = nbest > 1 ? new ChartCellNbestFactory(nbest, nbestBeam, maxSentenceLength,
				super.lexicalCategories) : modelFactory.isUsingDependencies() ? Cell1Best.factory()
						: Cell1BestTreeBased.factory();
	}

	@Override
	List<Scored<SyntaxTreeNode>> parseAstar(final InputToParser input) {

		cellFactory.newSentence();
		final List<InputWord> sentence = input.getInputWords();
		final Model model = modelFactory.make(input);
		final int sentenceLength = sentence.size();
		final PriorityQueue<AgendaItem> agenda = new PriorityQueue<>(1000);
		model.buildAgenda(agenda, sentence);
		final ChartCell[][] chart = new ChartCell[sentenceLength][sentenceLength];

		final List<Scored<SyntaxTreeNode>> result = new ArrayList<>(nbest);
		int chartSize = 0;

		// Track which cells in the chart are non-empty. This is helpful, because the A* chart is very sparse compared
		// to CKY charts.
		final List<List<ChartCell>> cellsStartingAt = new ArrayList<>(sentenceLength + 1);
		final List<List<ChartCell>> cellsEndingAt = new ArrayList<>(sentenceLength + 1);
		for (int i = 0; i < sentenceLength + 1; i++) {
			cellsStartingAt.add(new ArrayList<>());
			cellsEndingAt.add(new ArrayList<>());
		}

		// Dummy final cell that the complete parses are stored in.
		final ChartCell finalCell = cellFactory.make();

		while (chartSize < maxChartSize
				&& (result.isEmpty() || (result.size() < nbest && !agenda.isEmpty() && agenda.peek().getCost() > nbestBeam
						* result.get(0).getScore()))) {
			// Add items from the agenda, until we have enough parses.

			final AgendaItem agendaItem = agenda.poll();
			if (agendaItem == null) {
				break;
			}

			// Try to put an entry in the chart.
			ChartCell cell = chart[agendaItem.getStartOfSpan()][agendaItem.getSpanLength() - 1];
			if (cell == null) {
				cell = cellFactory.make();
				chart[agendaItem.getStartOfSpan()][agendaItem.getSpanLength() - 1] = cell;
				cellsStartingAt.get(agendaItem.getStartOfSpan()).add(cell);
				cellsEndingAt.get(agendaItem.getStartOfSpan() + agendaItem.getSpanLength()).add(cell);
			}

			if (cell.add(agendaItem)) {
				chartSize++;
				// If a new entry was added, update the agenda.

				// Is the new entry an acceptable complete parse?
				if (agendaItem.getSpanLength() == sentenceLength
						&& agendaItem.getInsideScore() > Double.NEGATIVE_INFINITY
						&& (possibleRootCategories.isEmpty() || possibleRootCategories.contains(agendaItem.getParse()
								.getCategory())) &&
						// For N-best parsing, the final cell checks if that the final parse is unique. e.g. if it's
								// dependencies are unique, ignoring the category
						finalCell.add("", agendaItem)) {
					result.add(new Scored<>(agendaItem.getParse(), agendaItem.getInsideScore()));
				}

				// See if any Unary Rules can be applied to the new entry.
				updateAgendaUnary(model, agendaItem, agenda);

				// See if the new entry can be the left argument of any binary rules.
				for (final ChartCell rightCell : cellsStartingAt.get(agendaItem.getStartOfSpan()
						+ agendaItem.getSpanLength())) {
					for (final AgendaItem rightEntry : rightCell.getEntries()) {
						updateAgenda(agenda, agendaItem, rightEntry, model);
					}
				}

				// See if the new entry can be the right argument of any binary
				// rules.
				for (final ChartCell leftCell : cellsEndingAt.get(agendaItem.getStartOfSpan())) {
					for (final AgendaItem leftEntry : leftCell.getEntries()) {
						updateAgenda(agenda, leftEntry, agendaItem, model);
					}
				}
			}
		}

		if (result.size() == 0) {
			// Parse failure.
			return null;
		}

		return result;

	}

	/**
	 * Updates the agenda with of any unary rules that can be applied.
	 */
	private void updateAgendaUnary(final Model model, final AgendaItem newItem, final PriorityQueue<AgendaItem> agenda) {
		final SyntaxTreeNode parse = newItem.getParse();
		final List<UnaryRule> ruleProductions = unaryRules.get(parse.getCategory());
		final int size = ruleProductions.size();
		if (size == 0) {
			return;
		}

		final boolean isNotPunctuationNode = parse.getRuleType() != RuleType.LP && parse.getRuleType() != RuleType.RP;
		for (int i = 0; i < size; i++) {
			final UnaryRule unaryRule = ruleProductions.get(i);
			if (isNotPunctuationNode || unaryRule.isTypeRaising()) {
				// Don't allow unary rules to apply to the output of non-type-raising rules.
				// i.e. don't allow both (NP (N ,))
				// The reason for allowing type-raising is to simplify Eisner Normal Form contraints (a
				// punctuation rule would mask the fact that a rule is the output of type-raising).
				// TODO should probably refactor the constraint into NormalForm.

				SyntaxTreeNodeUnary newNode;

				if (usingDependencies) {
					final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
					newNode = new SyntaxTreeNodeUnary(unaryRule.getResult(), parse, unaryRule
							.getDependencyStructureTransformation().apply(parse.getDependencyStructure(),
									resolvedDependencies), unaryRule, resolvedDependencies);
				} else {
					newNode = new SyntaxTreeNodeUnary(unaryRule.getResult(), parse, null, unaryRule, null);
				}

				agenda.add(model.unary(newItem, newNode, unaryRule));
			}
		}
	}

	/**
	 * Updates the agenda with the result of all combinators that can be applied to leftChild and rightChild.
	 */
	private void updateAgenda(final PriorityQueue<AgendaItem> agenda, final AgendaItem left, final AgendaItem right,
			final Model model) {

		final SyntaxTreeNode leftChild = left.getParse();
		final SyntaxTreeNode rightChild = right.getParse();

		if (!seenRules.isSeen(leftChild.getCategory(), rightChild.getCategory())) {
			return;
		}
		final List<RuleProduction> rules = getRules(leftChild.getCategory(), rightChild.getCategory());

		final int size = rules.size();
		for (int i = 0; i < size; i++) {
			final RuleProduction production = rules.get(i);
			// Check if normal-form constraints let us add this rule.
			if (NormalForm.isOk(leftChild.getRuleClass(), rightChild.getRuleClass(), production.getRuleType(),
					leftChild.getCategory(), rightChild.getCategory(), production.getCategory(),
					left.getStartOfSpan() == 0)) {

				final SyntaxTreeNodeBinary newNode;
				if (usingDependencies) {
					// Update all the information for tracking dependencies.
					final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
					final DependencyStructure newDependencies = production.getCombinator().apply(
							leftChild.getDependencyStructure(), rightChild.getDependencyStructure(),
							resolvedDependencies);

					final boolean headIsLeft = newDependencies.getArbitraryHead() == leftChild.getDependencyStructure()
							.getArbitraryHead();

					newNode = new SyntaxTreeNodeBinary(production.getCategory(), leftChild, rightChild,
							production.getRuleType(), headIsLeft, newDependencies, resolvedDependencies);

				} else {
					// If we're not modeling dependencies, we can save a lot of work.
					newNode = new SyntaxTreeNodeBinary(production.getCategory(), leftChild, rightChild,
							production.getRuleType(), production.isHeadIsLeft(), null, null);
				}

				agenda.add(model.combineNodes(left, right, newNode));
			}
		}
	}
}
