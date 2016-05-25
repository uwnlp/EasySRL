package edu.uw.easysrl.syntax.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.syntax.parser.ChartCell.Cell1Best;
import edu.uw.easysrl.syntax.parser.ChartCell.Cell1BestTreeBased;
import edu.uw.easysrl.syntax.parser.ChartCell.CellNoDynamicProgram;
import edu.uw.easysrl.syntax.parser.ChartCell.ChartCellFactory;
import edu.uw.easysrl.syntax.parser.ChartCell.ChartCellNbestFactory;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util.Scored;

public class ParserAStar extends AbstractParser {

	protected final ModelFactory modelFactory;

	protected final int maxChartSize;
	protected final int maxAgendaSize;
	protected final ChartCellFactory cellFactory;
	protected final boolean usingDependencies;
	protected final List<ParserListener> listeners;

	@Deprecated
	public ParserAStar(final ModelFactory modelFactory, final int maxSentenceLength, final int nbest,
			final List<Category> validRootCategories, final File modelFolder, final int maxChartSize)
			throws IOException {
		super(TaggerEmbeddings.loadCategories(new File(modelFolder, "categories")), maxSentenceLength, nbest,
				validRootCategories, modelFolder);
		this.modelFactory = modelFactory;
		this.maxChartSize = maxChartSize;
		this.usingDependencies = modelFactory.isUsingDependencies();
		this.cellFactory = chooseCellFactory(modelFactory, nbest);

		// Get default arguments for newer parameters.
		final ParserBuilder builder = new Builder(modelFolder);
		this.maxAgendaSize = builder.getMaxAgendaSize();
		this.listeners = builder.getListeners();
	}

	protected ChartCellFactory chooseCellFactory(final ModelFactory modelFactory, final int nbest) {
		final ChartCellFactory cellFactory;
		if (!this.modelFactory.isUsingDynamicProgram()) {
			cellFactory = CellNoDynamicProgram.factory();
		} else if (nbest > 1) {
			cellFactory = new ChartCellNbestFactory(this.nbest, this.nbestBeam, super.maxLength,
					super.lexicalCategories);
		} else if (modelFactory.isUsingDependencies()) {
			cellFactory = Cell1Best.factory();
		} else {
			cellFactory = Cell1BestTreeBased.factory();
		}
		return cellFactory;
	}

	protected ParserAStar(final Builder builder) {
		super(builder);
		this.modelFactory = builder.getModelFactory();
		this.maxChartSize = builder.getMaxChartSize();
		this.maxAgendaSize = builder.getMaxAgendaSize();
		this.listeners = builder.getListeners();
		this.usingDependencies = modelFactory.isUsingDependencies();
		this.cellFactory = chooseCellFactory(modelFactory, nbest);
	}

	@Override
	protected List<Scored<SyntaxTreeNode>> parse(final InputToParser input) {
		final ChartCellFactory sentenceCellFactory = cellFactory.forNewSentence();
		final List<InputWord> sentence = input.getInputWords();
		for (final ParserListener listener : listeners) {
			listener.handleNewSentence(sentence);
		}
		final Model model = modelFactory.make(input);
		final int sentenceLength = sentence.size();
		final Agenda agenda = model.makeAgenda();
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
		final ChartCell finalCell = sentenceCellFactory.make();

		while (chartSize < maxChartSize
				&& !agenda.isEmpty()
				&& agenda.size() < maxAgendaSize
				&& (result.isEmpty() || (result.size() < nbest &&
					agenda.peek().getCost() > result.get(0).getScore() + Math.log(nbestBeam)))) {
			// Add items from the agenda, until we have enough parses.
			final AgendaItem agendaItem = agenda.peek();
			if (agendaItem.getInsideScore() <= Double.NEGATIVE_INFINITY) {
				break;
			}

			// Try to put an entry in the chart.
			ChartCell cell = chart[agendaItem.getStartOfSpan()][agendaItem.getSpanLength() - 1];
			if (cell == null) {
				cell = sentenceCellFactory.make();
				chart[agendaItem.getStartOfSpan()][agendaItem.getSpanLength() - 1] = cell;
				cellsStartingAt.get(agendaItem.getStartOfSpan()).add(cell);
				cellsEndingAt.get(agendaItem.getStartOfSpan() + agendaItem.getSpanLength()).add(cell);
			}

			if (cell.add(agendaItem)) {
				boolean keepParsing = true;
				for (final ParserListener listener : listeners) {
					keepParsing = keepParsing && listener.handleChartInsertion(agenda);
				}
				if (!keepParsing) {
					break;
				}

				chartSize++;
				agenda.poll();
				// If a new entry was added, update the agenda.

				// Is the new entry an acceptable complete parse?
				if (agendaItem.getSpanLength() == sentenceLength
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
			} else {
				agenda.poll();
			}
		}

		final List<Scored<SyntaxTreeNode>> finalResult = result.isEmpty() ? null : result;

		for (final ParserListener listener : listeners) {
			listener.handleSearchCompletion(finalResult, agenda, chartSize);
		}

		return finalResult;
	}

	/**
	 * Updates the agenda with of any unary rules that can be applied.
	 */
	protected void updateAgendaUnary(final Model model, final AgendaItem newItem, final Agenda agenda) {
		final SyntaxTreeNode parse = newItem.getParse();
		final List<UnaryRule> ruleProductions = unaryRules.get(parse.getCategory());
		if (ruleProductions.isEmpty()) {
			return;
		}
		final boolean isNotPunctuationNode = parse.getRuleType() != RuleType.LP && parse.getRuleType() != RuleType.RP;
		for (final UnaryRule unaryRule : ruleProductions) {
			if (isNotPunctuationNode || unaryRule.isTypeRaising()) {
				// Don't allow unary rules to apply to the output of non-type-raising rules.
				// i.e. don't allow both (NP (N ,))
				// The reason for allowing type-raising is to simplify Eisner Normal Form constraints (a
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

				if (isValidStep(newNode)) {
					agenda.add(model.unary(newItem, newNode, unaryRule));
				}
			}
		}
	}

	/**
	 * Updates the agenda with the result of all combinators that can be applied to leftChild and rightChild.
	 */
	protected void updateAgenda(final Agenda agenda, final AgendaItem left, final AgendaItem right, final Model model) {

		final SyntaxTreeNode leftChild = left.getParse();
		final SyntaxTreeNode rightChild = right.getParse();

		if (!allowUnseenRules && !seenRules.isSeen(leftChild.getCategory(), rightChild.getCategory())) {
			return;
		}
		final List<RuleProduction> rules = getRules(leftChild.getCategory(), rightChild.getCategory());

		final int size = rules.size();
		for (int i = 0; i < size; i++) {
			final RuleProduction production = rules.get(i);
			// Check if normal-form constraints let us add this rule.
			if (normalForm.isOk(leftChild.getRuleClass(), rightChild.getRuleClass(), production.getRuleType(),
					leftChild.getCategory(), rightChild.getCategory(), production.getCategory(),
					left.getStartOfSpan() == 0)) {

				final SyntaxTreeNodeBinary newNode;
				if (usingDependencies) {
					// Update all the information for tracking dependencies.
					final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
					final DependencyStructure newDependencies = production.getCombinator().apply(
							leftChild.getDependencyStructure(), rightChild.getDependencyStructure(),
							resolvedDependencies);

					newNode = new SyntaxTreeNodeBinary(production.getCategory(), leftChild, rightChild,
							production.getRuleType(), production.isHeadIsLeft(), newDependencies, resolvedDependencies);

				} else {
					// If we're not modeling dependencies, we can save a lot of work.
					newNode = new SyntaxTreeNodeBinary(production.getCategory(), leftChild, rightChild,
							production.getRuleType(), production.isHeadIsLeft(), null, null);
				}

				if (isValidStep(newNode)) {
					agenda.add(model.combineNodes(left, right, newNode));
				}
			}
		}
	}

	protected boolean isValidStep(final SyntaxTreeNode node) {
		return true;
	}

	public Parser make(final File modelFolder) {
		return new Builder(modelFolder).build();
	}

	public static class Builder extends ParserBuilder<Builder> {

		public Builder(final File modelFolder) {
			super(modelFolder);
			super.maxChartSize(20000);
		}

		@Override
		protected ParserAStar build2() {
			return new ParserAStar(this);
		}

	}
}
