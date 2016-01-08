package edu.uw.easysrl.semantics.lexicon;

import java.util.Optional;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.semantics.Constant;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.LogicParser;
import edu.uw.easysrl.semantics.SemanticType;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;

public class NumbersLexicon extends Lexicon {

	@Override
	public Logic getEntry(final String word, final String pos, final Category category, final Coindexation coindexation,
			final Optional<CCGandSRLparse> parse, final int wordIndex) {
		// Special case numbers
		if (category == Category.ADJECTIVE && pos.equals("CD")) {
			// Lots of room for improvement here...
			return LogicParser.fromString("#y#p#x.p(x) & eq(size(x), y)", Category.valueOf("(N/N)/NP")).apply(
					new Constant(getLemma(word, pos, parse, wordIndex), SemanticType.makeFromCategory(Category.NP)));
		}

		return null;
	}

	@Override
	public boolean isMultiWordExpression(final SyntaxTreeNode node) {
		return node.getCategory() == Category.ADJECTIVE
				&& node.getLeaves().stream().allMatch(x -> x.getPos().startsWith("CD"));

	}
}
