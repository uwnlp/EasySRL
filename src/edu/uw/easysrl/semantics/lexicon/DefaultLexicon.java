package edu.uw.easysrl.semantics.lexicon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.semantics.AtomicSentence;
import edu.uw.easysrl.semantics.ConnectiveSentence;
import edu.uw.easysrl.semantics.ConnectiveSentence.Connective;
import edu.uw.easysrl.semantics.Constant;
import edu.uw.easysrl.semantics.LambdaExpression;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.OperatorSentence;
import edu.uw.easysrl.semantics.OperatorSentence.Operator;
import edu.uw.easysrl.semantics.QuantifierSentence;
import edu.uw.easysrl.semantics.QuantifierSentence.Quantifier;
import edu.uw.easysrl.semantics.SemanticType;
import edu.uw.easysrl.semantics.Sentence;
import edu.uw.easysrl.semantics.SkolemTerm;
import edu.uw.easysrl.semantics.Variable;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;

/**
 * Automatically constructs a default semantics for a word based on its category
 *
 */
public class DefaultLexicon extends Lexicon {
	private static final Constant ANSWER = new Constant("ANSWER", SemanticType.E);
	/**
	 * Useful to distinguish auxiliary and implicative verbs, which have the same category (S\NP)/(S\NP).
	 */
	private final ImmutableSet<String> auxiliaryVerbs = ImmutableSet.of("be", "do", "have", "go");
	private final String ARG = "ARG";

	// POS tags for content words.
	private final static Set<String> contentPOS = ImmutableSet.of("NN", "VB", "JJ", "RB", "PR", "RP", "IN", "PO");

	private final static List<SRLLabel> noLabels = Arrays.asList(SRLFrame.NONE, SRLFrame.NONE, SRLFrame.NONE,
			SRLFrame.NONE, SRLFrame.NONE, SRLFrame.NONE);

	@Override
	public Logic getEntry(final String word, final String pos, final Category category,
			final Coindexation coindexation, final Optional<CCGandSRLparse> parse, final int wordIndex) {
		final String lemma = getLemma(word, pos, parse, wordIndex);

		if (Category.valueOf("conj|conj").matches(category)) {
			// Special case, since we have other rules to deal with this non-compositionally.
			return new Constant(lemma, SemanticType.E);
		}

		final List<SRLLabel> labels;
		if (parse.isPresent()) {
			final List<ResolvedDependency> deps = parse.get().getOrderedDependenciesAtPredicateIndex(wordIndex);

			// Find the semantic role for each argument of the category.
			labels = deps.stream().map(x -> x == null ? SRLFrame.NONE : x.getSemanticRole())
					.collect(Collectors.toList());

		} else {
			labels = noLabels;
		}

		final HeadAndArguments headAndArguments = new HeadAndArguments(category, coindexation);

		return LambdaExpression.make(
				getEntry(lemma, category, coindexation, headAndArguments.argumentVariables,
						headAndArguments.headVariable, null, headAndArguments.coindexationIDtoVariable,
						isContentWord(lemma, pos, category), labels), headAndArguments.argumentVariables);
	}

	private boolean isContentWord(final String word, final String pos, final Category category) {
		if (!(pos.length() > 1 && contentPOS.contains(pos.substring(0, 2)))) {
			return false;
		} else if (auxiliaryVerbs.contains(word)
				&& (Category.valueOf("(S\\NP)/(S\\NP)").matches(category) || Category.valueOf("(S[q]/(S\\NP))/NP")
						.matches(category))) {
			// Auxiliary verbs
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Recursively builds up logical form, add interpretations for arguments from right to left.
	 */
	private Logic getEntry(final String word, final Category category, final Coindexation coindexation,
			final List<Variable> vars, final Variable head, final Sentence resultSoFar,
			final Map<Coindexation.IDorHead, Variable> idToVar, final boolean isContentWord, final List<SRLLabel> labels) {

		SRLLabel label = labels.size() == 0 ? SRLFrame.NONE : labels.get(labels.size() - 1);
		if (category.getNumberOfArguments() > 0 && category.getLeft().isModifier() && labels.size() > 1
				&& labels.get(labels.size() - 2) != SRLFrame.NONE) {
			// For transitive modifier categories, like (S\S)/NP or ((S\NP)\(S\NP)/NP move the semantic role to the
			// argument.
			// Hacky, but simplifies things a lot.
			label = labels.get(labels.size() - 2);
			labels.set(labels.size() - 2, SRLFrame.NONE);
		}

		if (Category.valueOf("(NP|NP)|(NP|NP)").matches(category)
				|| Category.valueOf("(PP|PP)|(PP|PP)").matches(category)) {
			// Hate these categories soo much.
			return head;
		}

		// A label for this argument, either a semantic role or "ARG".
		final String argumentLabel = (label == SRLFrame.NONE) ? ARG : label.toString();

		if (category.getNumberOfArguments() == 0) {
			// Base case. N, S, NP etc.
			if (Category.N.matches(category) || Category.S.matches(category.getArgument(0))) {
				return ConnectiveSentence
						.make(Connective.AND, resultSoFar != null && (word == null || !isContentWord) ? null
								: new AtomicSentence(word, head), resultSoFar);
			} else if (category == Category.NP || category == Category.PP) {
				if (resultSoFar == null) {
					// Pronouns, named-entities, etc. are just constants.
					return new Constant(word, SemanticType.E);
				}

				// NP/N --> sk(#x . p(x))
				return new SkolemTerm(new LambdaExpression(resultSoFar, head));
			} else if (category == Category.PR) {
				return new Constant(word, SemanticType.E);
			} else {
				return new Constant(word, SemanticType.E);
			}
		} else if (isFunctionIntoEntityModifier(category) && category.getNumberOfArguments() == 1) {
			if (resultSoFar == null) {
				// PP/NP --> #x . x
				return head;
			} else {
				// Functions into NP\NP get special treatment.
				// (NP\NP)/(S\NP) --> #p#x . sk(#y . p(y) & x=y)
				final Variable y = new Variable(SemanticType.E);
				return new SkolemTerm(new LambdaExpression(ConnectiveSentence.make(Connective.AND, new AtomicSentence(
						equals, head, y), resultSoFar), y));
			}

		} else {
			final Variable predicate = vars.get(0);
			if (category.isModifier() && coindexation.isModifier()) {
				// Modifier categories.
				// (S\NP)/(S\NP) --> #p#x#e . lemma(e) & p(x,e)
				// S/S --> #p#e . lemma(e) & p(e)
				// (S/S)/(S/S) --> #p#q#e . lemma(e) & p(e) & q(p, e)
				final Sentence modifier;
				final AtomicSentence px = new AtomicSentence(predicate, vars.subList(1, vars.size()));
				if (resultSoFar != null || word == null || !isContentWord) {
					// Non-content word modifiers
					modifier = null;
				} else if (label == SRLFrame.NONE) {
					// No semantic role: foo(e)
					modifier = new AtomicSentence(word, head);
				} else if (label == SRLFrame.NEG) {
					// Special case negation
					return ConnectiveSentence.make(Connective.AND, new OperatorSentence(Operator.NOT, px), resultSoFar);
				} else if (label.isCoreArgument()) {
					// Core Semantic role: "raging bull" --> #x . bull(x) & \exists e[rage(e) & AO(x,e)]
					final Variable event = new Variable(SemanticType.Ev);
					modifier = new QuantifierSentence(Quantifier.EXISTS, event, ConnectiveSentence.make(Connective.AND,
							new AtomicSentence(word, event), new AtomicSentence(argumentLabel, head, event)));
				} else {
					// Adjunct Semantic role: TMP(yesterday, e)
					modifier = new AtomicSentence(argumentLabel, new Constant(word, SemanticType.E), head);
				}

				return ConnectiveSentence.make(Connective.AND, px, modifier, resultSoFar);

			} else {

				return getEntryComplexCategories(word, category, coindexation, vars, head, resultSoFar, idToVar,
						isContentWord, labels, label, argumentLabel);
			}
		}
	}

	private Logic getEntryComplexCategories(final String word, final Category category,
			final Coindexation coindexation, final List<Variable> vars, final Variable head,
			final Sentence resultSoFar, final Map<Coindexation.IDorHead, Variable> idToVar,
			final boolean isContentWord, final List<SRLLabel> labels, final SRLLabel label, final String argumentLabel) {
		// Other complex categories.
		final Category argument = category.getRight();
		final Variable predicate = vars.get(0);
		Sentence resultForArgument;

		if (isWhQuestion(category) && (argument.equals(Category.NP) || argument.equals(Category.PP))) {
			// wh-questions. The NP argument is the answer.
			resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(equals, predicate, ANSWER),
					resultSoFar);
		} else if (isDuplicateArgument(coindexation)) {
			// Avoid creating duplicate argument.
			resultForArgument = resultSoFar;
		} else if (argument.getNumberOfArguments() == 0) {
			resultForArgument = getEntryComplexCategoriesWithAtomicArgument(word, category, head, resultSoFar,
					isContentWord, label, argumentLabel, predicate);

		} else if (Category.valueOf("NP|NP").matches(argument) || Category.valueOf("NP|N").matches(argument)
				|| Category.valueOf("(N\\N)/NP").matches(argument)
				|| Category.valueOf("(NP|NP)|(NP|NP)").matches(argument)) {
			// Very rare and weird (NP/NP) arguments. Discard these.
			resultForArgument = resultSoFar;
		} else {
			resultForArgument = getEntryComplexCategoryWithComplexArgument(category, coindexation, head, resultSoFar,
					idToVar, argumentLabel, predicate);
		}

		// Recursively build up the next interpretation for the other arguments.
		return getEntry(word, category.getLeft(), coindexation.getLeft(), vars.subList(1, vars.size()), head,
				resultForArgument, idToVar, isContentWord, labels.subList(0, labels.size() - 1));
	}

	private Sentence getEntryComplexCategoryWithComplexArgument(final Category category,
			final Coindexation coindexation, final Variable head, final Sentence resultSoFar,
			final Map<Coindexation.IDorHead, Variable> idToVar, final String argumentLabel, final Variable predicate) {
		final Category argument = category.getRight();
		Sentence resultForArgument;
		// Complex argument, e.g. ((S[dcl]\NP)/NP_1)/(S[to]\NP_1)

		// Build up a list of the arguments of the argument. If these arguments aren't supplied elsewhere,
		// we
		// need to quantify them.
		final List<Logic> argumentsOfArgument = new ArrayList<>(argument.getNumberOfArguments());
		final List<Variable> toQuantify = new ArrayList<>();
		Coindexation coindexationOfArgument = coindexation.getRight();
		for (int i = argument.getNumberOfArguments(); i > 0; i--) {
			// Iterate over arguments of argument.
			Logic argumentSemantics;
			final Coindexation.IDorHead id = coindexationOfArgument.getRight().getID();
			if (idToVar.containsKey(id)) {
				// Argument is co-indexed with another argument.
				final Variable coindexedVar = idToVar.get(id);
				final SemanticType variableType = coindexedVar.getType();
				final SemanticType expectedType = SemanticType.makeFromCategory(argument.getArgument(i));
				if (variableType == SemanticType.EtoT && expectedType == SemanticType.E) {
					// NP argument coindexed with N
					if (isWhQuestion(category)) {
						// Question where the N is the answer, as in Which dog barks?
						// sk(#x.p(x) & x=ANSWER)
						final Variable x = new Variable(SemanticType.E);
						argumentSemantics = new SkolemTerm(new LambdaExpression(ConnectiveSentence.make(Connective.AND,
								new AtomicSentence(coindexedVar, x), new AtomicSentence(equals, x, ANSWER)), x));
					} else {
						// sk(#x.p(x))
						argumentSemantics = makeSkolem(coindexedVar);
					}
				} else {
					argumentSemantics = idToVar.get(id);
				}
			} else if (isWhQuestion(category) && argument.getArgument(i) == Category.NP && resultSoFar == null) {
				// Categories like: S[wq]/(S[dcl\NP)
				argumentSemantics = ANSWER;

			} else {
				// Not coindexed - make a new variable, and quantify it later.
				final Category argumentOfArgument = argument.getArgument(i);
				final Variable var = new Variable(SemanticType.makeFromCategory(argumentOfArgument));
				toQuantify.add(var);
				argumentSemantics = var;

			}
			argumentsOfArgument.add(argumentSemantics);
			coindexationOfArgument = coindexationOfArgument.getLeft();
		}

		// We need to add an extra variable for functions into N and S
		// e.g. an S\NP argument p needs arguments p(x, e)
		if (argument.getArgument(0) == Category.N || Category.S.matches(argument.getArgument(0))) {
			final Coindexation.IDorHead id = coindexationOfArgument.getID();
			if (idToVar.containsKey(id)) {
				argumentsOfArgument.add(idToVar.get(id));
			} else {
				final Variable var = new Variable(argument.getArgument(0) == Category.N ? SemanticType.E
						: SemanticType.Ev);
				toQuantify.add(var);
				argumentsOfArgument.add(var);
			}
		}

		Sentence argumentSemantics = new AtomicSentence(predicate, argumentsOfArgument);
		if (toQuantify.contains(argumentsOfArgument.get(argumentsOfArgument.size() - 1))
				&& !argumentsOfArgument.contains(head)) {
			// Link the head of the argument back to the head of the construction.
			argumentSemantics = ConnectiveSentence.make(Connective.AND, argumentSemantics, new AtomicSentence(
					argumentLabel, argumentsOfArgument.get(argumentsOfArgument.size() - 1), head));
		}

		// Existentially quantify any missing variables
		for (final Variable var : toQuantify) {
			argumentSemantics = new QuantifierSentence(Quantifier.EXISTS, var, argumentSemantics);
		}

		resultForArgument = ConnectiveSentence.make(Connective.AND, argumentSemantics, resultSoFar);
		return resultForArgument;
	}

	private Sentence getEntryComplexCategoriesWithAtomicArgument(final String word, final Category category,
			final Variable head, final Sentence resultSoFar, final boolean isContentWord, final SRLLabel label,
			final String argumentLabel, final Variable predicate) {
		final Category argument = category.getRight();
		Sentence resultForArgument;
		// Atomic argument, e.g. S\NP
		if (argument.equals(Category.NP) || argument.equals(Category.PP)) {
			if (category.isFunctionIntoModifier()) {
				// No semantic role --> on:(S\S)/NP --> #x#p#e . p(e) & on(x,e)
				// With semantic role --> on:(S\S)/NP --> #x#p#e . p(e) & TMP(x,e)
				resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(
						label == SRLFrame.NONE ? word : argumentLabel, predicate, head), resultSoFar);

			} else {
				// (S\NP)/NP --> ... & arg(y, x)
				resultForArgument = isContentWord ? ConnectiveSentence.make(Connective.AND, new AtomicSentence(
						argumentLabel, predicate, head), resultSoFar) : resultSoFar;
			}
		} else if (Category.N.matches(argument)) {
			if (head.getType() == SemanticType.Ev) {
				// S/N --> #p#e . ARG(sk(#x.p(x)),e)
				resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(argumentLabel,
						makeSkolem(predicate), head), resultSoFar);
			} else {
				// NP/N --> ... & p(x)
				resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(predicate, head),
						resultSoFar);
			}

		} else if (argument.equals(Category.PR) || argument.equals(Category.NPthr) || argument.equals(Category.NPexpl)) {
			// Semantically vacuous arguments
			resultForArgument = resultSoFar;
		} else if (Category.S.matches(argument)) {
			// N/S --> ... & p(ev)
			final Variable ev = new Variable(SemanticType.Ev);
			resultForArgument = ConnectiveSentence.make(
					Connective.AND,
					new QuantifierSentence(Quantifier.EXISTS, ev, ConnectiveSentence.make(Connective.AND,
							new AtomicSentence(predicate, ev), new AtomicSentence(argumentLabel, ev, head))),
							resultSoFar);
		} else {
			throw new IllegalStateException();
		}
		return resultForArgument;
	}

	private boolean isWhQuestion(final Category category) {
		return category.isFunctionInto(Category.valueOf("S[wq]"))
				&& !category.isFunctionInto(Category.valueOf("S[dcl]")) // Hacky way of avoiding matches to S[X]
				&& !category.isFunctionIntoModifier();
	}

	private SkolemTerm makeSkolem(final Logic predicate) {
		final Variable x = new Variable(SemanticType.E);
		return new SkolemTerm(new LambdaExpression(new AtomicSentence(predicate, x), x));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edu.uw.easysrl.semantics.lexicon.AbstractLexicon#isMultiWordExpression(edu.uw.easysrl.syntax.grammar.SyntaxTreeNode
	 * )
	 */
	@Override
	public boolean isMultiWordExpression(final SyntaxTreeNode node) {
		if (node.getLeaves().stream().allMatch(x -> x.getPos().startsWith("NNP"))) {
			// Analyze "Barack Obama" as barack_obama, not #x.barack(x)&obama(x)
			return true;
		} else if (node.getCategory() == Category.CONJ && node.getRuleType() != RuleType.LP
				&& node.getRuleType() != RuleType.RP) {
			// Don't bother trying to do multi-word conjunctions compositionally (e.g. "as well as").
			return true;
		}
		return false;
	}

	/**
	 * Looks for cases like (S_1/(S_1\NP_2))/NP_2 or (NP_1/(N_1/PP_2))\NP_2 where we don't want to make _2 an argument
	 * of _1 twice.
	 */
	private boolean isDuplicateArgument(Coindexation indexation) {
		final Coindexation argument = indexation.getRight();
		while (indexation.getLeft() != null) {
			indexation = indexation.getLeft();
			Coindexation otherArgument = indexation.getRight();
			if (otherArgument != null && indexation.getLeftMost().getID().equals(otherArgument.getLeftMost().getID())) {
				while (otherArgument.getLeft() != null) {
					if (argument.getID().equals(otherArgument.getRight().getID())) {
						return true;
					}

					otherArgument = otherArgument.getLeft();
				}
			}
		}

		return false;
	}
}
