package edu.uw.easysrl.semantics.lexicon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.semantics.SemanticType;
import edu.uw.easysrl.semantics.Variable;
import edu.uw.easysrl.syntax.grammar.Category;

/**
 * Given a category and coindexation, builds variables corresponding to the head and each argument of the category
 *
 */
class HeadAndArguments {
	final Map<Coindexation.IDorHead, Variable> coindexationIDtoVariable;
	final List<Variable> argumentVariables;
	final Variable headVariable;

	HeadAndArguments(final Category category, final Coindexation coindexation) {

		// First, find create a semantic argument for each syntactic argument.
		coindexationIDtoVariable = new HashMap<>();
		argumentVariables = new ArrayList<>(category.getNumberOfArguments());
		Coindexation tmp = coindexation;
		for (int i = category.getNumberOfArguments(); i > 0; i--) {
			final Variable var = new Variable(SemanticType.makeFromCategory(category.getArgument(i)));
			argumentVariables.add(var);
			if (category.getArgument(i).getNumberOfArguments() == 0) {
				coindexationIDtoVariable.put(tmp.getRight().getID(), var);
			}
			tmp = tmp.getLeft();
		}

		// Find which variable is the head. Create an extra variable for N and S categories,
		// which are semantically functions.
		headVariable = getHead(category, argumentVariables);
		coindexationIDtoVariable.put(tmp.getID(), headVariable);

	}

	private Variable getHead(final Category category, final List<Variable> vars) {
		Variable head;
		if (Category.N.matches(category.getArgument(0))) {
			// Ns are #x . foo(x)
			head = new Variable(SemanticType.E);
			vars.add(head);
		} else if (Category.S.matches(category.getArgument(0))) {
			// Ss are #e . foo(e)
			head = new Variable(SemanticType.Ev);
			vars.add(head);
		} else if (Lexicon.isFunctionIntoEntityModifier(category)) {
			// Functions into PP/PP or PP/NP should be #x . x
			head = vars.get(vars.size() - 1);
		} else if (category.isFunctionInto(Category.NP) || category.isFunctionInto(Category.PP)) {
			head = new Variable(SemanticType.E);
		} else {
			// Punctation, particles, coordination etc.
			head = null;
		}
		return head;
	}

}