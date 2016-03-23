package edu.uw.easysrl.semantics.lexicon;

import java.util.List;
import java.util.Optional;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.lemmatizer.MorphaStemmer;
import edu.uw.easysrl.semantics.Constant;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.SemanticType;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;

/**
 * Builds logical forms for words, based on their categories and semantic dependencies.
 *
 */
public abstract class Lexicon {
	final Constant equals = new Constant("eq", SemanticType.make(SemanticType.E, SemanticType.EtoT));

	/**
	 * Builds a semantic representation for the i'th word of a parse.
	 */
	public Logic getEntry(final CCGandSRLparse parse, final int wordIndex) {
		final SyntaxTreeNodeLeaf leaf = parse.getLeaf(wordIndex);
		return getEntry(leaf.getWord(), leaf.getPos(), leaf.getCategory(),
				// The parser may or may not have recorded what the co-indexation for word is, so re-building it here
				// anyway.
				DependencyStructure.make(leaf.getCategory(), leaf.getWord(), wordIndex).getCoindexation(),
				Optional.of(parse), wordIndex);

	}

	public abstract Logic getEntry(String word, String pos, Category category, Coindexation coindexation,
			Optional<CCGandSRLparse> parse, int wordIndex);

	/**
	 * Builds a semantic representation.
	 */
	public Logic getEntry(final String word, final String pos, final Category category, final Coindexation coindexation) {
		return getEntry(word, pos, category, coindexation, Optional.empty(), -1);
	}

	public abstract boolean isMultiWordExpression(SyntaxTreeNode node);

	/**
	 * Gets a lemma for a phrase. Multiword phrases are collapsed into a single token. If a parse is provided, it will
	 * collapse verb-particle constructions.
	 */
	public String getLemma(final String word, final String pos, final Optional<CCGandSRLparse> parse, final int wordIndex) {
		String lemma = word == null ? null : MorphaStemmer.stemToken(word.toLowerCase().replaceAll(" ", "_"), pos);
		if (parse.isPresent()) {
			final List<ResolvedDependency> deps = parse.get().getOrderedDependenciesAtPredicateIndex(wordIndex);
			for (final ResolvedDependency dep : deps) {
				if (dep != null && dep.getCategory().getArgument(dep.getArgNumber()) == Category.PR) {
					// Merge predicates in verb-particle constructions, e.g. pick_up
					lemma = lemma + "_" + parse.get().getLeaf(dep.getArgumentIndex()).getWord();
				}
			}

		}

		return lemma;
	}

	/** Functions into e.g. (NP\NP)|$ */
	static boolean isFunctionIntoEntityModifier(final Category category) {
		return category.getNumberOfArguments() > 0
				&& (category.getArgument(0).equals(Category.PP) || category.getArgument(0).equals(Category.NP))
				&& (category.getArgument(1).equals(Category.NP) || category.getArgument(1).equals(Category.PP));
	}
}
