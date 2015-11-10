package edu.uw.easysrl.syntax.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.DependencyStructure.UnlabelledDependency;
import edu.uw.easysrl.main.EasySRL.InputFormat;
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
import edu.uw.easysrl.util.Util.Scored;

public class ParserAStar extends AbstractParser {

	private final ModelFactory modelFactory;

	private final int maxChartSize;

	public ParserAStar(final ModelFactory modelFactory, final int maxSentenceLength, final int nbest,
			final double nbestBeam, final InputFormat inputFormat, final List<Category> validRootCategories,
			final File modelFolder, final int maxChartSize) throws IOException {
		super(modelFactory.getLexicalCategories(), maxSentenceLength, nbest, nbestBeam, inputFormat,
				validRootCategories, modelFolder);
		this.modelFactory = modelFactory;
		this.maxChartSize = maxChartSize;
	}

	@Override
	List<Scored<SyntaxTreeNode>> parseAstar(final List<InputWord> sentence) {

		final Model model = modelFactory.make(sentence);
		final int sentenceLength = sentence.size();
		final PriorityQueue<AgendaItem> agenda = new PriorityQueue<>();
		model.buildAgenda(agenda, sentence);
		final ChartCell[][] chart = new ChartCell[sentenceLength][sentenceLength];

		final List<Scored<SyntaxTreeNode>> result = new ArrayList<>();
		int chartSize = 0;

		while (chartSize < maxChartSize && (result.isEmpty() || (result.size() < nbest
		// TODO && agenda.peek() != null && agenda.peek().getCost() >
		// result.get(0).getProbability() + nbestBeam
				))) {
			// Add items from the agenda, until we have enough parses.

			final AgendaItem agendaItem = agenda.poll();
			if (agendaItem == null) {
				break;
			}

			// Try to put an entry in the chart.
			ChartCell cell = chart[agendaItem.getStartOfSpan()][agendaItem.getSpanLength() - 1];
			if (cell == null) {
				cell = nbest > 1 ? new CellNBest() : new Cell1Best();
				chart[agendaItem.getStartOfSpan()][agendaItem.getSpanLength() - 1] = cell;
			}

			if (cell.add(agendaItem)) {
				chartSize++;
				// If a new entry was added, update the agenda.

				if (agendaItem.getStartOfSpan() == 0 && agendaItem.getSpanLength() == sentenceLength
						&& agendaItem.getInsideScore() > Double.NEGATIVE_INFINITY
						&& possibleRootCategories.contains(agendaItem.getParse().getCategory())) {
					result.add(new Scored<>(agendaItem.getParse(), agendaItem.getInsideScore()));
				}

				// See if any Unary Rules can be applied to the new entry.

				for (final UnaryRule unaryRule : unaryRules.get(agendaItem.getParse().getCategory())) {
					if ((agendaItem.getParse().getRuleType() != RuleType.LP && agendaItem.getParse().getRuleType() != RuleType.RP)
							|| unaryRule.getCategory().isTypeRaised()) {
						// Don't allow unary rules to apply to the output of non-type-raising rules.
						// i.e. don't allow both (NP (N ,))
						// The reason for allowing type-raising is to simplify Eisner Normal Form contraints (a
						// punctuation rule would mask the fact that a rule is the output of type-raising).
						// TODO should probably refactor the constraint into NormalForm.

						final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
						agenda.add(model.unary(
								agendaItem,
								new SyntaxTreeNodeUnary(unaryRule.getResult(), agendaItem.getParse(), unaryRule
										.getDependencyStructureTransformation().apply(
												agendaItem.getParse().getDependencyStructure(), resolvedDependencies),
												unaryRule, resolvedDependencies), unaryRule));
					}
				}

				// See if the new entry can be the left argument of any binary
				// rules.
				for (int spanLength = agendaItem.getSpanLength() + 1; spanLength < 1 + sentenceLength
						- agendaItem.getStartOfSpan(); spanLength++) {

					final ChartCell rightCell = chart[agendaItem.getStartOfSpan() + agendaItem.getSpanLength()][spanLength
							- agendaItem.getSpanLength() - 1];
					if (rightCell == null) {
						continue;
					}

					for (final AgendaItem rightEntry : rightCell.getEntries()) {
						if (rightEntry.getParse().getResolvedUnlabelledDependencies().isEmpty()) {
							updateAgenda(agenda, agendaItem, rightEntry, sentenceLength, model);

						}

					}
				}

				// See if the new entry can be the right argument of any binary
				// rules.
				for (int startOfSpan = 0; startOfSpan < agendaItem.getStartOfSpan(); startOfSpan++) {
					final int spanLength = agendaItem.getStartOfSpan() + agendaItem.getSpanLength() - startOfSpan;

					final ChartCell leftCell = chart[startOfSpan][spanLength - agendaItem.getSpanLength() - 1];
					if (leftCell == null) {
						continue;
					}
					for (final AgendaItem leftEntry : leftCell.getEntries()) {
						if (leftEntry.getParse().getResolvedUnlabelledDependencies().isEmpty()) {
							updateAgenda(agenda, leftEntry, agendaItem, sentenceLength, model);
						}
					}
				}

			}
		}

		if (chart[0][sentenceLength - 1] == null) {
			// Parse failure.
			return null;
		}

		return result;

	}

	/**
	 * Updates the agenda with the result of all combinators that can be applied to leftChild and rightChild.
	 */
	private void updateAgenda(final PriorityQueue<AgendaItem> agenda, final AgendaItem left, final AgendaItem right,
			final int sentenceLength, final Model model) {

		final SyntaxTreeNode leftChild = left.getParse();
		final SyntaxTreeNode rightChild = right.getParse();

		if (!seenRules.isSeen(leftChild.getCategory(), rightChild.getCategory())) {
			return;
		}
		final int spanLength = left.getSpanLength() + right.getSpanLength();

		for (final RuleProduction production : getRules(leftChild.getCategory(), rightChild.getCategory())) {
			if (!NormalForm.isOk(leftChild.getRuleClass(), rightChild.getRuleClass(), production.getRuleType(),
					leftChild.getCategory(), rightChild.getCategory(), production.getCategory(),
					left.getStartOfSpan() == 0)) {
				continue;
			} else if (spanLength == sentenceLength && !possibleRootCategories.contains(production.getCategory())) {
				// Enforce the root node has a prespecified category. Doesn't
				// allow unary rules in spanning cell.
				continue;
			} else {

				final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
				final DependencyStructure newDependencies = production.getCombinator().apply(
						leftChild.getDependencyStructure(), rightChild.getDependencyStructure(), resolvedDependencies);
				final boolean headIsLeft = newDependencies.getArbitraryHead() == leftChild.getDependencyStructure()
						.getArbitraryHead();
				final SyntaxTreeNodeBinary newNode = new SyntaxTreeNodeBinary(production.getCategory(), leftChild,
						rightChild, production.getRuleType(), headIsLeft, newDependencies, resolvedDependencies);
				agenda.add(model.combineNodes(left, right, newNode));
			}
		}
	}
}
