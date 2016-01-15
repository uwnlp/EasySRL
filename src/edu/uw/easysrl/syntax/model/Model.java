package edu.uw.easysrl.syntax.model;

import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;

public abstract class Model {

	public static abstract class ModelFactory {
		public abstract Model make(InputToParser sentence);

		public abstract Collection<Category> getLexicalCategories();

		public abstract boolean isUsingDependencies();
	}

	private final int sentenceLength;
	private final double[][] outsideScoresUpperBound;

	protected Model(final int sentenceLength) {
		this.sentenceLength = sentenceLength;
		this.outsideScoresUpperBound = new double[sentenceLength + 1][sentenceLength + 1];
	}

	abstract double getUpperBoundForWord(int index);

	public abstract void buildAgenda(PriorityQueue<AgendaItem> queue, List<InputWord> words);

	public abstract AgendaItem combineNodes(AgendaItem leftChild, AgendaItem rightChild, SyntaxTreeNode node);

	public abstract AgendaItem unary(AgendaItem child, SyntaxTreeNode result, UnaryRule rule);

	public double getOutsideUpperBound(final int start, final int end) {
		return outsideScoresUpperBound[start][end];
	}

	protected double computeOutsideProbabilities() {
		double total = 0.0;
		final double[] fromLeft = new double[sentenceLength + 1];
		final double[] fromRight = new double[sentenceLength + 1];

		fromLeft[0] = 0.0;
		fromRight[sentenceLength] = 0.0;

		for (int i = 0; i < sentenceLength - 1; i++) {
			final int j = sentenceLength - i;

			fromLeft[i + 1] = fromLeft[i] + getUpperBoundForWord(i);
			fromRight[j - 1] = fromRight[j] + getUpperBoundForWord(j - 1);
			total += getUpperBoundForWord(i);
		}

		total += getUpperBoundForWord(sentenceLength - 1);

		for (int i = 0; i < sentenceLength + 1; i++) {
			for (int j = i; j < sentenceLength + 1; j++) {
				outsideScoresUpperBound[i][j] = fromLeft[i] + fromRight[j];
			}
		}

		return total;
	}

}