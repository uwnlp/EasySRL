package edu.uw.easysrl.syntax.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleClass;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.syntax.parser.ChartCell.Cell1BestCKY;
import edu.uw.easysrl.util.Util.Scored;

public class ParserCKY extends AbstractParser {

	@Deprecated
	public ParserCKY(final ModelFactory modelFactory, final int maxSentenceLength, final int nbest,
			final List<Category> validRootCategories, final File modelFolder, final int maxChartSize)
			throws IOException {
		super(modelFactory.getLexicalCategories(), maxSentenceLength, nbest, validRootCategories, modelFolder);
		this.maxChartSize = maxChartSize;
		this.modelFactory = modelFactory;

		// Get default arguments for newer parameters.
		final ParserBuilder builder = new Builder(modelFolder);
		this.listeners = builder.getListeners();
	}

	protected ParserCKY(final Builder builder) {
		super(builder);
		this.maxChartSize = builder.getMaxChartSize();
		this.modelFactory = builder.getModelFactory();
		this.listeners = builder.getListeners();
	}

	private final int maxChartSize;
	private final ModelFactory modelFactory;
	private final List<ParserListener> listeners;

	@Override
	protected List<Scored<SyntaxTreeNode>> parse(final InputToParser input) {

		final int numWords = input.length();
		if (input.length() > maxLength) {
			return null;
		}
		for (final ParserListener listener : listeners) {
			listener.handleNewSentence(input.getInputWords());
		}

		final ChartCell[][] chart = new ChartCell[numWords][numWords];
		final Model model = modelFactory.make(input);

		// Add lexical categories
		final Agenda agenda = model.makeAgenda();
		model.buildAgenda(agenda, input.getInputWords());
		int size = 0;
		for (final AgendaItem item : agenda) {
			ChartCell cell = chart[item.getStartOfSpan()][item.getSpanLength() - 1];
			if (cell == null) {
				cell = createCell();
				chart[item.getStartOfSpan()][item.getSpanLength() - 1] = cell;
			}
			final int previousCellSize = cell.size();
			boolean keepParsing = addEntry(cell, item, model);
			size += cell.size() - previousCellSize;
			if (!keepParsing) {
				for (final ParserListener listener : listeners) {
					listener.handleSearchCompletion(null, null, size);
				}
				return null;
			}
		}

		for (int spanLength = 2; spanLength <= numWords; spanLength++) {
			for (int startOfSpan = 0; startOfSpan <= numWords - spanLength; startOfSpan++) {
				final ChartCell newCell = makeChartCell(chart, startOfSpan, spanLength, model);

				chart[startOfSpan][spanLength - 1] = newCell;
				size += newCell == null ? 0 : newCell.size();

				if (newCell == null || size > maxChartSize) {
					for (final ParserListener listener : listeners) {
						listener.handleSearchCompletion(null, null, size);
					}
					return null;
				}
			}
		}

		final List<AgendaItem> parses = new ArrayList<>();
		for (final AgendaItem entry : chart[0][numWords - 1].getEntries()) {
			parses.add(entry);
		}
		Collections.sort(parses);

		final List<Scored<SyntaxTreeNode>> result = parses.stream()
				.filter(a -> possibleRootCategories.contains(a.getParse().getCategory()))
				.map(a -> new Scored<>(a.getParse(), a.getInsideScore()))
				.limit(1)
				.collect(Collectors.toList());

		final List<Scored<SyntaxTreeNode>> finalResult = result.isEmpty() ? null : result;

		for (final ParserListener listener : listeners) {
			listener.handleSearchCompletion(finalResult, null, size);
		}
		return finalResult;
	}

	ChartCell makeChartCell(final ChartCell[][] chart, final int startOfSpan, final int spanLength, final Model model) {

		final ChartCell newCell = createCell();
		for (int spanSplit = 1; spanSplit < spanLength; spanSplit++) {
			final ChartCell left = chart[startOfSpan][spanSplit - 1];
			final ChartCell right = chart[startOfSpan + spanSplit][spanLength - spanSplit - 1];

			if (!makeChartCell(newCell, left, right, model)) {
				return null;
			}
		}
		return newCell;
	}

	ChartCell createCell() {
		return new Cell1BestCKY();
	}

	private boolean makeChartCell(final ChartCell result, final ChartCell left, final ChartCell right, final Model model) {

		for (final AgendaItem l : left.getEntries()) {
			for (final AgendaItem r : right.getEntries()) {

				if (!allowUnseenRules && !seenRules.isSeen(l.getParse().getCategory(), r.getParse().getCategory())) {
					continue;
				}

				for (final RuleProduction rule : Combinator.getRules(l.getParse().getCategory(), r.getParse()
						.getCategory(), binaryRules)) {

					final RuleClass leftRuleClass = l.getParse().getRuleType().getNormalFormClassForRule();
					final RuleType ruleType = rule.getRuleType();
					final RuleClass rightRuleClass = r.getParse().getRuleType().getNormalFormClassForRule();

					if (!normalForm.isOk(leftRuleClass, rightRuleClass, ruleType, l.getParse().getCategory(), r
							.getParse().getCategory(), rule.getCategory(), l.getStartOfSpan() == 0)) {
						continue;
					}
					final SyntaxTreeNode newNode;

					if (l.getParse().hasDependencies()) {
						final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
						final DependencyStructure deps = rule.getCombinator().apply(
								l.getParse().getDependencyStructure(), r.getParse().getDependencyStructure(),
								resolvedDependencies);

						newNode = new SyntaxTreeNodeBinary(rule.getCategory(), l.getParse(), r.getParse(), ruleType,
								rule.isHeadIsLeft(), deps, resolvedDependencies);
					} else {
						newNode = new SyntaxTreeNodeBinary(rule.getCategory(), l.getParse(), r.getParse(), ruleType,
								rule.isHeadIsLeft(), null, null);
					}

					final AgendaItem newItem = model.combineNodes(l, r, newNode);

					if (!addEntry(result, newItem, model)) {
						return false;
					}
				}
			}
		}
		return true;

	}

	private boolean addEntry(final ChartCell result, final AgendaItem newItem, final Model model) {

		final Category category = newItem.getParse().getCategory();

		if (result.add(newItem)) {
			for (final ParserListener listener : listeners) {
				if (!listener.handleChartInsertion(null)) {
					return false;
				}
			}
			for (final UnaryRule unary : unaryRules.get(category)) {
				final SyntaxTreeNode unaryNode;
				if (newItem.getParse().hasDependencies()) {
					final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
					final DependencyStructure newDeps = unary.getDependencyStructureTransformation().apply(
							newItem.getParse().getDependencyStructure(), resolvedDependencies);

					unaryNode = new SyntaxTreeNodeUnary(unary.getCategory(), newItem.getParse(), newDeps, unary,
							resolvedDependencies);

				} else {
					unaryNode = new SyntaxTreeNodeUnary(unary.getCategory(), newItem.getParse(), null, unary, null);
				}
				final AgendaItem newUnary = model.unary(newItem, unaryNode, unary);

				if (!addEntry(result, newUnary, model)) {
					return false;
				}
			}
		}

		return true;
	}

	public static class Builder extends ParserBuilder<Builder> {

		public Builder(final File modelFolder) {
			super(modelFolder);
			super.maxChartSize(300000);
		}

		@Override
		protected ParserCKY build2() {
			return new ParserCKY(this);
		}
	}
}
