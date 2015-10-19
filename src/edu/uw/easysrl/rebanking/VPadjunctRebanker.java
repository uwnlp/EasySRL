package edu.uw.easysrl.rebanking;

import java.util.List;

import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;

/*
 * Replaces (S[to]\\NP)/(S[b]\\NP)-->(S\NP)\(S\NP) unaries with ((S\\NP)\\(S\\NP))/(S[b]\\NP). Eliminates a unary rule, and means the PURPOSE dependency is lexicalized.
 */
public class VPadjunctRebanker extends Rebanker {

	@Override
	boolean dontUseAsTrainingExample(final Category c) {
		return false;
	}

	@Override
	boolean doRebanking(final List<SyntaxTreeNodeLeaf> result, final Sentence sentence) {
		return updateNode(result, sentence.getCcgbankParse());

	}

	private boolean updateNode(final List<SyntaxTreeNodeLeaf> result, final SyntaxTreeNode node) {

		boolean change = false;

		if (node.getChildren().size() == 2 && isVPtoAdverb(node.getChild(1))

				) {

			if (node.getChild(1).getLeaves().get(0).getCategory().equals(Category.valueOf("(S[to]\\NP)/(S[b]\\NP)"))) {
				// He retired (to spend time with family)
				setCategory(result, node.getChild(1).getLeaves().get(0).getHeadIndex(),
						Category.valueOf("((S\\NP)\\(S\\NP))/(S[b]\\NP)"));
				change = true;

			}

		}

		for (final SyntaxTreeNode child : node.getChildren()) {
			change = change || updateNode(result, child);
		}
		return change;
	}

	private boolean isVP(final Category cat) {
		return cat.equals(Category.valueOf("S[pss]\\NP")) || cat.equals(Category.valueOf("S[to]\\NP"))
				|| cat.equals(Category.valueOf("S[adj]\\NP")) || cat.equals(Category.valueOf("S[ng]\\NP"));
	}

	private boolean isVPtoAdverb(final SyntaxTreeNode node) {
		if (node.getChildren().size() == 1 && node.getCategory().equals(Category.valueOf("(S\\NP)\\(S\\NP)"))
				&& isVP(node.getChild(0).getCategory())) {
			return true;
		}
		return false;
	}

}
