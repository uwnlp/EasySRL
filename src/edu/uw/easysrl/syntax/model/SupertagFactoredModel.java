package edu.uw.easysrl.syntax.model;

import com.google.common.base.Preconditions;

import java.util.Collection;
import java.util.List;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.Tagger.ScoredCategory;

public class SupertagFactoredModel extends Model {

	private final List<List<ScoredCategory>> tagsForWords;
	private final boolean includeDependencies;

	public SupertagFactoredModel(final List<List<ScoredCategory>> tagsForWords, final boolean includeDependencies) {
		super(tagsForWords.size());
		this.includeDependencies = includeDependencies;
		this.tagsForWords = tagsForWords;
		computeOutsideProbabilities();
	}

	@Override
	public void buildAgenda(final Agenda agenda, final List<InputWord> words) {
		for (int i = 0; i < words.size(); i++) {
			final InputWord word = words.get(i);
			for (final ScoredCategory cat : tagsForWords.get(i)) {
				agenda.add(new AgendaItem(new SyntaxTreeNodeLeaf(word.word, word.pos, word.ner, cat.getCategory(), i,
						includeDependencies), cat.getScore(), getOutsideUpperBound(i, i + 1), i, 1, includeDependencies));
			}
		}
	}

	@Override
	public AgendaItem combineNodes(final AgendaItem leftChild, final AgendaItem rightChild, final SyntaxTreeNode node) {
		final int length = leftChild.spanLength + rightChild.spanLength;

		// Add a penalty based on length of distance between the heads of the two children.
		// This implements the 'attach low' heuristic.
		final int depLength = Math.abs(leftChild.getParse().getHeadIndex() - rightChild.getParse().getHeadIndex());
		double lengthPenalty = 0.00001 * depLength;

		// Extra penalty for clitics, to really make sure they attach locally.
		if (rightChild.getSpanLength() == 1 && rightChild.getParse().getWord().startsWith("'")) {
			lengthPenalty = lengthPenalty * 10;
		}

		return new AgendaItem(node, leftChild.getInsideScore() + rightChild.getInsideScore() - lengthPenalty,
				getOutsideUpperBound(leftChild.startOfSpan, leftChild.startOfSpan + length), leftChild.startOfSpan,
				length, includeDependencies);

	}

	@Override
	public AgendaItem unary(final AgendaItem child, final SyntaxTreeNode result, final UnaryRule rule) {
		return new AgendaItem(result, child.getInsideScore() - 0.1, child.outsideScoreUpperbound, child.startOfSpan,
				child.spanLength, includeDependencies);
	}

	@Override
	public double getUpperBoundForWord(final int index) {
		return tagsForWords.get(index).get(0).getScore();
	}

	public static class SupertagFactoredModelFactory extends ModelFactory {
    	private final Tagger tagger;
    	private final Collection<Category> lexicalCategories;
    	private final boolean includeDependencies;

		public SupertagFactoredModelFactory(final Tagger tagger,
                                        	final Collection<Category> lexicalCategories,
                                        	final boolean includeDependencies) {
			super();
			this.tagger = tagger;
			this.lexicalCategories = lexicalCategories;
			this.includeDependencies = includeDependencies;
		}

		@Override
		public SupertagFactoredModel make(final InputToParser input) {
      		if (input.isAlreadyTagged()) {
        		return new SupertagFactoredModel(input.getInputSupertags(), includeDependencies);
      		} else {
        		Preconditions.checkNotNull(tagger, "Inputs should be already tagged if no tagger is given.");
        		return new SupertagFactoredModel(tagger.tag(input.getInputWords()),
                                         includeDependencies);
      		}
		}

		@Override
		public Collection<Category> getLexicalCategories() {
			return lexicalCategories;
		}

		@Override
		public boolean isUsingDependencies() {
			return includeDependencies;
		}
	}
}
