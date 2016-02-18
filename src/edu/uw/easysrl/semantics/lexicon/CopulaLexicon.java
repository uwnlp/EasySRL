package edu.uw.easysrl.semantics.lexicon;

import java.util.List;
import java.util.Optional;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.semantics.AtomicSentence;
import edu.uw.easysrl.semantics.Constant;
import edu.uw.easysrl.semantics.LambdaExpression;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.SemanticType;
import edu.uw.easysrl.semantics.Variable;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;

public class CopulaLexicon extends Lexicon {

	@Override
	public Logic getEntry(final String word, final String pos, final Category category, final Coindexation coindexation,
			final Optional<CCGandSRLparse> parse, final int wordIndex) {
		if (parse.isPresent() && getLemma(word, pos, parse, wordIndex).equals("be")
				&& category.getNumberOfArguments() == 2
				&& (category.getArgument(1).equals(Category.NP) || category.getArgument(1).equals(Category.PP))
				&& (category.getArgument(2).equals(Category.NP) || category.getArgument(2).equals(Category.PP))) {
			final HeadAndArguments headAndArguments = new HeadAndArguments(category, coindexation);
			final List<ResolvedDependency> deps = parse.get().getOrderedDependenciesAtPredicateIndex(wordIndex);
			return makeCopulaVerb(deps, headAndArguments.argumentVariables, headAndArguments.headVariable, parse.get());

		}
		return null;
	}

	@Override
	public boolean isMultiWordExpression(final SyntaxTreeNode node) {
		return false;
	}

	/**
	 * Special-case semantics for copular verbs:
	 *
	 * TODO verbs with expletive arguments and 'other' prepositions
	 */
	private Logic makeCopulaVerb(final List<ResolvedDependency> deps, final List<Variable> vars, final Variable head,
			final CCGandSRLparse parse) {
		Logic statement;
		if (deps.get(1) != null && deps.get(1).getPreposition() != Preposition.NONE) {
			// S\NP/PP_on --> on(x,y,e)
			statement = new AtomicSentence(getPrepositionPredicate(deps, 1, parse), vars.get(1), vars.get(0), head);
		} else {
			if (deps.get(0) != null && deps.get(0).getPreposition() != Preposition.NONE) {
				// Can happen in questions: S[q]/PP/NP Is the ball on the table?
				statement = new AtomicSentence(getPrepositionPredicate(deps, 0, parse), vars.get(0), vars.get(1), head);
			} else {
				// S\NP/NP
				SemanticType type = SemanticType.T;
				type = SemanticType.make(head.getType(), type);
				type = SemanticType.make(vars.get(1).getType(), type);
				type = SemanticType.make(vars.get(0).getType(), type);
				
				Constant pred = new Constant("eq", type);
				
				statement = new AtomicSentence(pred, vars.get(0), vars.get(1), head);
			}
		}

		return LambdaExpression.make(statement, vars);
	}

	private String getPrepositionPredicate(final List<ResolvedDependency> deps, final int arg,
			final CCGandSRLparse parse) {
		final Preposition preposition = deps.get(arg).getPreposition();
		String result = preposition.toString();
		if (preposition == Preposition.OTHER) {
			// Hack to fill in the prepositions the parse didn't track. Look for PP/NP nodes whose argument is the same
			// as the PP arg of the verb.
			for (final ResolvedDependency dep : parse.getCcgParse().getAllLabelledDependencies()) {
				if (dep.getCategory().equals(Category.valueOf("PP/NP"))
						&& dep.getArgumentIndex() == deps.get(arg).getArgumentIndex()) {
					result = parse.getCcgParse().getLeaves().get(dep.getHead()).getWord();
				}
			}
		}

		return result.toLowerCase();
	}
}
