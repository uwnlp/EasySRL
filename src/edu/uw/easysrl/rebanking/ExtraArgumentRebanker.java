package edu.uw.easysrl.rebanking;

import java.util.List;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;

public class ExtraArgumentRebanker extends Rebanker {

	@Override
	boolean dontUseAsTrainingExample(final Category c) {
		return false;
	}

	@Override
	boolean doRebanking(final List<SyntaxTreeNodeLeaf> result, final Sentence sentence) {
		boolean change = false;

		for (final SyntaxTreeNodeLeaf node : result) {
			if (isPredicate(sentence, node)) {

				final Category category = node.getCategory();
				for (int i = 1; i <= category.getNumberOfArguments(); i++) {
					final Category arg = category.getArgument(i);

					if (Category.valueOf("PP/NP").matches(arg)) {
						setCategory(result, node.getHeadIndex(), category.replaceArgument(i, Category.PR));

						for (final CCGBankDependency toArg : sentence.getCCGBankDependencyParse().getArgument(
								node.getHeadIndex(), i)) {
							final int argIndex = toArg.getSentencePositionOfArgument();
							setCategory(result, argIndex, Category.valueOf("PR"));

						}
						change = true;

					}

					if (Category.valueOf("S\\NP").matches(arg)
							&&
							// &&
							!isPropbankCoreArgument(node.getHeadIndex(), i, sentence, true)
							&& !(category.isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) && i == 2)) {

						if (Category.valueOf("S[adj]\\NP").matches(arg) || Category.valueOf("PP/NP").matches(arg)) {
							// 222 of these

							setCategory(result, node.getHeadIndex(), category.replaceArgument(i, Category.PR));

							// TODO rare S[adj]\NP)/PP arguments
							for (final CCGBankDependency toArg : sentence.getCCGBankDependencyParse().getArgument(
									node.getHeadIndex(), i)) {
								final int argIndex = toArg.getSentencePositionOfArgument();
								setCategory(result, argIndex, Category.valueOf("PR"));

							}
							change = true;

						} else if (Category.valueOf("S[to]\\NP").equals(arg)) {
							// 139 of these

							for (final CCGBankDependency toArg : sentence.getCCGBankDependencyParse().getArgument(
									node.getHeadIndex(), i)) {
								final int argIndex = toArg.getSentencePositionOfArgument();
								for (final CCGBankDependency toTo : sentence.getCCGBankDependencyParse()
										.getDependencies()) {
									if (toTo.getSentencePositionOfArgument() == argIndex
											&& Category.valueOf("(S[to]\\NP)/(S[b]\\NP)").equals(
													result.get(toTo.getSentencePositionOfPredicate()).getCategory())) {
										change = true;
										final int toIndex = toTo.getSentencePositionOfPredicate();
										setCategory(result, toIndex, Category.valueOf("((S\\NP)\\(S\\NP))/(S[b]\\NP)"));
									}
								}

								//

							}

							if (change) {
								setCategory(result, node.getHeadIndex(), category.replaceArgument(i, null));

							}
						}
					}

					if (Category.valueOf("PP").matches(arg)
							&& !isPropbankCoreArgument(node.getHeadIndex(), i, sentence, true)) {

						boolean removePParg = false;

						for (final CCGBankDependency toNP : sentence.getCCGBankDependencyParse().getArgument(
								node.getHeadIndex(), i)) {
							final int np = toNP.getSentencePositionOfArgument();
							if (np < node.getHeadIndex()) {
								// Must be PP in object extraction: The man we
								// depended on
								continue;
							}

							if (Category.valueOf("PP/(S\\NP)").matches(result.get(np).getCategory())) {

								removePParg = true;

								setCategory(
										result,
										np,
										result.get(np).getCategory()
												.replaceArgument(0, Category.valueOf("(S\\NP)\\(S\\NP)")));

							} else {
								for (final CCGBankDependency fromPP : sentence.getCCGBankDependencyParse()
										.getDependencies()) {
									if (fromPP.getSentencePositionOfArgument() == toNP.getSentencePositionOfArgument()
											&& result.get(fromPP.getSentencePositionOfPredicate()).getCategory()
											.isFunctionInto(Category.PP)) {
										final int pp = fromPP.getSentencePositionOfPredicate();
										setCategory(

												result,
												pp,
												result.get(pp).getCategory()
														.replaceArgument(0, Category.valueOf("(S\\NP)\\(S\\NP)")));
										removePParg = true;
									}
								}
							}
						}

						if (removePParg) {
							setCategory(result, node.getHeadIndex(), category.replaceArgument(i, null));
							change = true;
						}
					}

				}

			}

		}

		return change;

	}

}
