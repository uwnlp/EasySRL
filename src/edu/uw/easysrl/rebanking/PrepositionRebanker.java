package edu.uw.easysrl.rebanking;

import java.util.List;
import java.util.Map;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;

/**
 * Around 1% of PropBank deps are missing from CCGBank, because core arguments
 * are given the category ((S\NP)\(S\NP))/NP, not PP.
 *
 */
public class PrepositionRebanker extends Rebanker {

	@Override
	boolean dontUseAsTrainingExample(final Category c) {
		return false;
	}

	int changeCount = 0;
	int total = 0;

	@Override
	boolean doRebanking(final List<SyntaxTreeNodeLeaf> result,
			final Sentence sentence) {
		boolean change = false;
		int index = 0;
		final Map<SRLDependency, CCGBankDependency> deps = sentence
				.getCorrespondingCCGBankDependencies();
		for (final SyntaxTreeNodeLeaf word : sentence.getCcgbankParse()
				.getLeaves()) {

			if (word.getCategory().toString().equals("((S\\NP)\\(S\\NP))/NP")) {

				for (final CCGBankDependency child : sentence
						.getCCGBankDependencyParse().getDependencies(index)) {
					final int predicateIndex = child.getChild()
							.getSentencePosition();
					for (final SRLDependency dep : sentence.getSrlParse()
							.getDependenciesAtPredicateIndex(predicateIndex)) {

						if (dep.isCoreArgument()
								&& dep.getArgumentPositions().contains(index)
								&& deps.get(dep) == null) {

							setCategory(result, index, Category.PREPOSITION);
							final Category predicateCategory = result.get(
									predicateIndex).getCategory();

							// Update the predicate category. Try to find which
							// argument position the new PP should take.
							// ((S\NP)/NP)/PP
							CCGBankDependency previousArg = null;
							for (final CCGBankDependency otherArgument : sentence
									.getCCGBankDependencyParse()
									.getDependencies(predicateIndex)) {
								// System.out.println(d.getSentencePositionOfArgument());
								if (otherArgument
										.getSentencePositionOfArgument() > otherArgument
										.getSentencePositionOfPredicate()
										&& otherArgument
										.getSentencePositionOfArgument() < index
										&& (previousArg == null || otherArgument
										.getSentencePositionOfArgument() > previousArg
										.getSentencePositionOfArgument())) {
									previousArg = otherArgument;
								}
							}
							final int newArgumentNumber = previousArg == null ? predicateCategory
									.getNumberOfArguments() + 1 : previousArg
									.getArgNumber();

							final Category newPredicateCategory = predicateCategory
									.addArgument(newArgumentNumber, Category.PP);
							setCategory(result, predicateIndex,
									newPredicateCategory);

							change = true;
						}
					}
				}

			}

			index++;
		}

		return change;

	}

}
