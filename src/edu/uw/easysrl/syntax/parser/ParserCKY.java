package edu.uw.easysrl.syntax.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.DependencyStructure.UnlabelledDependency;
import edu.uw.easysrl.main.EasySRL.InputFormat;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleClass;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.NormalForm;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.util.Util.Scored;

public class ParserCKY extends AbstractParser {

	public ParserCKY(final ModelFactory modelFactory, final int maxSentenceLength, final int nbest,
			final double nbestBeam, final InputFormat inputFormat, final List<Category> validRootCategories,
			final File modelFolder, final int maxChartSize) throws IOException {
		super(modelFactory.getLexicalCategories(), maxSentenceLength, nbest, nbestBeam, inputFormat,
				validRootCategories, modelFolder);
		this.maxChartSize = maxChartSize;
		this.modelFactory = modelFactory;
	}

	private final int maxChartSize;
	private final ModelFactory modelFactory;

	@Override
	List<Scored<SyntaxTreeNode>> parseAstar(final List<InputWord> input) {

		final int numWords = input.size();
		if (input.size() > maxLength) {
			return null;
		}

		final ChartCell[][] chart = new ChartCell[numWords][numWords];
		final Model model = modelFactory.make(input);

		// Add lexical categories

		final PriorityQueue<AgendaItem> queue = new PriorityQueue<>();
		model.buildAgenda(queue, input);
		for (final AgendaItem item : queue) {
			ChartCell cell = chart[item.getStartOfSpan()][item.getSpanLength() - 1];
			if (cell == null) {
				cell = new Cell1BestCKY();
				chart[item.getStartOfSpan()][item.getSpanLength() - 1] = cell;
			}

			addEntry(cell, item, model);
		}

		int size = 0;
		for (int spanLength = 2; spanLength <= numWords; spanLength++) {
			for (int startOfSpan = 0; startOfSpan <= numWords - spanLength; startOfSpan++) {
				final ChartCell newCell = makeChartCell(chart, startOfSpan, spanLength, model);

				chart[startOfSpan][spanLength - 1] = newCell;
				size += newCell.getEntries().size();

				if (size > maxChartSize) {

					return null;
				}
			}
		}

		final List<AgendaItem> parses = new ArrayList<>(chart[0][numWords - 1].getEntries());
		Collections.sort(parses);

		final List<Scored<SyntaxTreeNode>> result = parses.stream()
				.filter(a -> super.possibleRootCategories.contains(a.getParse().getCategory()))
				.map(a -> new Scored<>(a.getParse(), a.getInsideScore())).collect(Collectors.toList());
		return result.size() == 0 ? null : result;
	}

	private ChartCell makeChartCell(final ChartCell[][] chart, final int startOfSpan, final int spanLength,
			final Model model) {

		final ChartCell newCell = new Cell1BestCKY();
		for (int spanSplit = 1; spanSplit < spanLength; spanSplit++) {
			final ChartCell left = chart[startOfSpan][spanSplit - 1];
			final ChartCell right = chart[startOfSpan + spanSplit][spanLength - spanSplit - 1];

			makeChartCell(newCell, left, right, model);
		}
		return newCell;
	}

	private void makeChartCell(final ChartCell result, final ChartCell left, final ChartCell right, final Model model) {

		for (final AgendaItem l : left.getEntries()) {
			for (final AgendaItem r : right.getEntries()) {

				if (!seenRules.isSeen(l.getParse().getCategory(), r.getParse().getCategory())) {
					continue;
				}

				// Normal form tags:
				// forward TR
				// backward TR
				// FC
				// GFC
				// BX
				// GBX
				// LP
				// RP
				// Lexicon

				for (final RuleProduction rule : Combinator.getRules(l.getParse().getCategory(), r.getParse()
						.getCategory(), binaryRules)) {

					final RuleClass leftRuleClass = l.getParse().getRuleType().getNormalFormClassForRule();
					final RuleType ruleType = rule.getRuleType();
					final RuleClass rightRuleClass = r.getParse().getRuleType().getNormalFormClassForRule();

					if (!NormalForm.isOk(leftRuleClass, rightRuleClass, ruleType, l.getParse().getCategory(), r
							.getParse().getCategory(), rule.getCategory(), l.getStartOfSpan() == 0)) {
						continue;
					}

					final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
					final DependencyStructure deps = rule.getCombinator().apply(l.getParse().getDependencyStructure(),
							r.getParse().getDependencyStructure(), resolvedDependencies);

					final SyntaxTreeNode newNode = new SyntaxTreeNodeBinary(rule.getCategory(), l.getParse(),
							r.getParse(), ruleType, rule.isHeadIsLeft(), deps, resolvedDependencies);
					final AgendaItem newItem = model.combineNodes(l, r, newNode);

					addEntry(result, newItem, model);
				}
			}
		}

	}

	private void addEntry(final ChartCell result, final AgendaItem newItem, final Model model) {

		final Category category = newItem.getParse().getCategory();

		if (result.add(newItem)) {
			for (final UnaryRule unary : unaryRules.get(category)) {
				final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
				final DependencyStructure newDeps = unary.getDependencyStructureTransformation().apply(
						newItem.getParse().getDependencyStructure(), resolvedDependencies);

				final SyntaxTreeNode unaryNode = new SyntaxTreeNodeUnary(unary.getCategory(), newItem.getParse(),
						newDeps, unary, resolvedDependencies);
				final AgendaItem newUnary = model.unary(newItem, unaryNode, unary);

				addEntry(result, newUnary, model);
			}
		}
	}

}
