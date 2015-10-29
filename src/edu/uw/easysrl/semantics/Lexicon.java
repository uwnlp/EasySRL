package edu.uw.easysrl.semantics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.DependencyStructure.IDorHead;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.lemmatizer.MorphaStemmer;
import edu.uw.easysrl.semantics.ConnectiveSentence.Connective;
import edu.uw.easysrl.semantics.OperatorSentence.Operator;
import edu.uw.easysrl.semantics.QuantifierSentence.Quantifier;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.util.Util;

/**
 * Builds logical forms for words, based on their categories and semantic dependencies.
 *
 */
public class Lexicon {
	private static final Constant ANSWER = new Constant("ANSWER", SemanticType.E);
	private static final String ALL_WORDS = "*ALL*";
	/**
	 * Useful to distinguish auxiliary and implicative verbs, which have the same category (S\NP)/(S\NP).
	 */
	private final ImmutableSet<String> auxiliaryVerbs = ImmutableSet.of("be", "do", "have", "go");
	private final String ARG = "ARG";
	private final Constant equals = new Constant("eq", SemanticType.make(SemanticType.E, SemanticType.EtoT));

	// POS tags for content words.
	private final static Set<String> contentPOS = ImmutableSet.of("NN", "VB", "JJ", "RB", "PR", "RP", "IN", "PO");

	private final static List<SRLLabel> noLabels = Arrays.asList(SRLFrame.NONE, SRLFrame.NONE, SRLFrame.NONE,
			SRLFrame.NONE, SRLFrame.NONE, SRLFrame.NONE);

	private final Table<String, Category, Logic> manualLexicon;

	public Lexicon() {

		this(ImmutableTable.of());
	}

	public Lexicon(final File lexiconFile) throws IOException {
		this(ImmutableTable.copyOf(loadLexicon(lexiconFile)));
	}

	private Lexicon(final Table<String, Category, Logic> manualLexicon) {
		this.manualLexicon = manualLexicon;
	}

	/**
	 * Builds a semantic representation for the i'th word of a parse.
	 */
	public Logic getEntry(final CCGandSRLparse parse, final int wordIndex) {
		final SyntaxTreeNodeLeaf leaf = parse.getLeaf(wordIndex);
		return getEntry(leaf.getWord(), leaf.getPos(), leaf.getCategory(),
				Coindexation.fromString(DependencyStructure.getMarkedUp(leaf.getCategory()), wordIndex),
				Optional.of(parse), wordIndex);

	}

	public Logic getEntry(final String word, final String pos, final Category category, final Coindexation coindexation) {
		return getEntry(word, pos, category, coindexation, Optional.empty(), -1);
	}

	private Logic getEntry(final String word, final String pos, final Category category,
			final Coindexation coindexation, final Optional<CCGandSRLparse> parse, final int wordIndex) {
		String lemma = word == null ? null : MorphaStemmer.stemToken(word.toLowerCase().replaceAll(" ", "_"), pos);

		// First, see if the user-defined lexicon file has an entry for this word+category
		final Logic manualLexiconResult = getManualLexicalEntry(lemma, category);
		if (manualLexiconResult != null) {
			return manualLexiconResult;
		}

		// Special case numbers
		if (category == Category.ADJECTIVE && pos.equals("CD")) {
			// Lots of room for improvement here...
			return LogicParser.fromString("#y#p#x.p(x) & eq(size(x), y)", Category.valueOf("(N/N)/NP")).apply(
					new Constant(lemma, SemanticType.EtoT));
		}

		// First, find create a semantic argument for each syntactic argument.
		final Map<IDorHead, Variable> idToVar = new HashMap<>();
		final List<Variable> vars = new ArrayList<>(category.getNumberOfArguments());
		Coindexation tmp = coindexation;
		for (int i = category.getNumberOfArguments(); i > 0; i--) {
			final Variable var = new Variable(SemanticType.makeFromCategory(category.getArgument(i)));
			vars.add(var);
			if (category.getArgument(i).getNumberOfArguments() == 0) {
				idToVar.put(tmp.getRight().getID(), var);
			}
			tmp = tmp.getLeft();
		}

		// Find which variable is the head. Create an extra variable for N and S categories,
		// which are semantically functions.
		final Variable head = getHead(category, vars);
		idToVar.put(tmp.getID(), head);

		final List<SRLLabel> labels;
		if (parse.isPresent()) {
			final List<ResolvedDependency> deps = parse.get().getOrderedDependenciesAtPredicateIndex(wordIndex);
			for (final ResolvedDependency dep : deps) {
				if (dep != null && dep.getCategory().getArgument(dep.getArgNumber()) == Category.PR) {
					// Merge predicates in verb-particle constructions, e.g. pick_up
					lemma = lemma + "_" + parse.get().getLeaf(dep.getArgumentIndex()).getWord();
				}
			}

			if (lemma.equals("be") && category.getNumberOfArguments() == 2
					&& (category.getArgument(1).equals(Category.NP) || category.getArgument(1).equals(Category.PP))
					&& (category.getArgument(2).equals(Category.NP) || category.getArgument(2).equals(Category.PP))) {
				return makeCopulaVerb(deps, vars, head, parse.get());

			}

			// Find the semantic role for each argument of the category.
			labels = deps.stream().map(x -> x == null ? SRLFrame.NONE : x.getSemanticRole())
					.collect(Collectors.toList());

		} else {
			labels = noLabels;
		}

		if (Category.valueOf("conj|conj").matches(category)) {
			// Special case, since we have other rules to deal with this non-compositionally.
			return new Constant(lemma, SemanticType.E);
		}

		return LambdaExpression.make(
				getEntry(lemma, category, coindexation, vars, head, null, idToVar, isContentWord(lemma, pos, category),
						labels), vars);
	}

	/**
	 * Special-case semantics for copular verbs:
	 *
	 * TODO verbs with expletive arguments and 'other' prepositions
	 */
	private Logic makeCopulaVerb(final List<ResolvedDependency> deps, final List<Variable> vars, final Variable head,
			final CCGandSRLparse parse) {
		Logic statement;
		if (deps.get(1).getPreposition() != Preposition.NONE) {
			// S\NP/PP_on --> on(x,y,e)
			statement = new AtomicSentence(getPrepositionPredicate(deps, 1, parse), vars.get(1), vars.get(0), head);
		} else {
			if (deps.get(0).getPreposition() != Preposition.NONE) {
				// Can happen in questions: S[q]/PP/NP Is the ball on the table?
				statement = new AtomicSentence(getPrepositionPredicate(deps, 0, parse), vars.get(0), vars.get(1), head);
			} else {
				// S\NP/NP
				statement = new AtomicSentence(equals, vars.get(0), vars.get(1), head);
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
					result = parse.getCcgParse().getLeaves().get(dep.getPredicateIndex()).getWord();
				}
			}
		}

		return result.toLowerCase();
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
		} else if (isFunctionIntoEntityModifier(category)) {
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

	/** Functions into e.g. (NP\NP)|$ */
	private boolean isFunctionIntoEntityModifier(final Category category) {
		return category.getNumberOfArguments() > 0
				&& (category.getArgument(0).equals(Category.PP) || category.getArgument(0).equals(Category.NP))
				&& (category.getArgument(1).equals(Category.NP) || category.getArgument(1).equals(Category.PP));
	}

	/**
	 * Recursively builds up logical form, add interpretations for arguments from right to left.
	 */
	private Logic getEntry(final String word, final Category category, final Coindexation coindexation,
			final List<Variable> vars, final Variable head, final Sentence resultSoFar,
			final Map<IDorHead, Variable> idToVar, final boolean isContentWord, final List<SRLLabel> labels) {

		SRLLabel label = labels.size() == 0 ? SRLFrame.NONE : labels.get(labels.size() - 1);
		if (category.getNumberOfArguments() > 0 && category.getLeft().isModifier() && labels.size() > 0
				&& labels.get(1) != SRLFrame.NONE) {
			// For transitive modifier categories, like (S\S)/NP move the semantic role to the argument.
			// Hacky, but simplifies things a lot.
			label = labels.get(1);
			labels.set(1, SRLFrame.NONE);
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
				} else {
					// Semantic role: TMP(yesterday, e)
					modifier = new AtomicSentence(argumentLabel, new Constant(word, SemanticType.E), head);
				}

				return ConnectiveSentence.make(Connective.AND, px, modifier, resultSoFar);

			} else {

				// Other complex categories.
				final Category argument = category.getRight();
				Sentence resultForArgument;

				if (isWhQuestion(category) && (argument.equals(Category.NP) || argument.equals(Category.PP))) {
					// wh-questions. The NP argument is the answer.
					resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(equals, predicate,
							ANSWER), resultSoFar);
				} else if (isDuplicateArgument(coindexation)) {
					// Avoid creating duplicate argument.
					resultForArgument = resultSoFar;
				} else if (argument.getNumberOfArguments() == 0) {
					// Atomic argument, e.g. S\NP
					if (argument.equals(Category.NP) || argument.equals(Category.PP)) {
						if (category.isFunctionIntoModifier()) {
							// No semantic role --> on:(S\S)/NP --> #x#p#e . p(e) & on(x,e)
							// With semantic role --> on:(S\S)/NP --> #x#p#e . p(e) & TMP(x,e)
							resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(
									label == SRLFrame.NONE ? word : argumentLabel, predicate, head), resultSoFar);

						} else {
							// (S\NP)/NP --> ... & arg(y, x)
							resultForArgument = isContentWord ? ConnectiveSentence.make(Connective.AND,
									new AtomicSentence(argumentLabel, predicate, head), resultSoFar) : resultSoFar;
						}
					} else if (Category.N.matches(argument)) {
						if (head.getType() == SemanticType.Ev) {
							// S/N --> #p#e . ARG(sk(#x.p(x)),e)
							resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(
									argumentLabel, makeSkolem(predicate), head), resultSoFar);
						} else {
							// NP/N --> ... & p(x)
							resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(predicate,
									head), resultSoFar);
						}

					} else if (argument.equals(Category.PR) || argument.equals(Category.NPthr)
							|| argument.equals(Category.NPexpl)) {
						// Semantically vacuous arguments
						resultForArgument = resultSoFar;
					} else if (Category.S.matches(argument)) {
						// N/S --> ... & p(ev)
						final Variable ev = new Variable(SemanticType.Ev);
						resultForArgument = ConnectiveSentence
								.make(Connective.AND,
										new QuantifierSentence(Quantifier.EXISTS, ev, ConnectiveSentence.make(
												Connective.AND, new AtomicSentence(predicate, ev), new AtomicSentence(
														argumentLabel, head, ev))), resultSoFar);
					} else {
						throw new IllegalStateException();
					}

				} else if (Category.valueOf("NP|NP").matches(argument) || Category.valueOf("NP|N").matches(argument)
						|| Category.valueOf("(N\\N)/NP").matches(argument)
						|| Category.valueOf("(NP|NP)|(NP|NP)").matches(argument)) {
					// Very rare and weird (NP/NP) arguments. Discard these.
					resultForArgument = resultSoFar;
				} else {
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
						final IDorHead id = coindexationOfArgument.getRight().getID();
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
									argumentSemantics = new SkolemTerm(new LambdaExpression(ConnectiveSentence.make(
											Connective.AND, new AtomicSentence(coindexedVar, x), new AtomicSentence(
													equals, x, ANSWER)), x));
								} else {
									// sk(#x.p(x))
									argumentSemantics = makeSkolem(coindexedVar);
								}
							} else {
								argumentSemantics = idToVar.get(id);
							}
						} else if (isWhQuestion(category) && argument.getArgument(i) == Category.NP
								&& resultSoFar == null) {
							// Categories like: S[wq]/(S[dcl\NP)
							argumentSemantics = ANSWER;
							;
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
						final IDorHead id = coindexationOfArgument.getID();
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
						argumentSemantics = ConnectiveSentence.make(
								Connective.AND,
								argumentSemantics,
								new AtomicSentence(argumentLabel,
										argumentsOfArgument.get(argumentsOfArgument.size() - 1), head));
					}

					// Existentially quantify any missing variables
					for (final Variable var : toQuantify) {
						argumentSemantics = new QuantifierSentence(Quantifier.EXISTS, var, argumentSemantics);
					}

					resultForArgument = ConnectiveSentence.make(Connective.AND, argumentSemantics, resultSoFar);
				}

				// Recursively build up the next interpretation for the other arguments.
				return getEntry(word, category.getLeft(), coindexation.getLeft(), vars.subList(1, vars.size()), head,
						resultForArgument, idToVar, isContentWord, labels.subList(0, labels.size() - 1));
			}
		}
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

	public boolean isMultiWordExpression(final SyntaxTreeNode node) {
		if (node.getLeaves().stream().allMatch(x -> x.getPos().startsWith("NNP"))) {
			// Analyze "Barack Obama" as barack_obama, not #x.barack(x)&obama(x)
			return true;
		} else if (node.getCategory() == Category.CONJ && node.getRuleType() != RuleType.LP
				&& node.getRuleType() != RuleType.RP) {
			// Don't bother trying to do multi-word conjunctions compositionally (e.g. "as well as").
			return true;
		} else if (node.getCategory() == Category.ADJECTIVE
				&& node.getLeaves().stream().allMatch(x -> x.getPos().startsWith("CD"))) {
			// Numbers
			return true;
		} else if (getManualLexicalEntry(node.getWord(), node.getCategory()) != null) {
			// Multiword expressions
			return true;
		}
		return false;
	}

	/**
	 * Load a manually defined lexicon
	 */
	private static Table<String, Category, Logic> loadLexicon(final File file) throws IOException {
		final Table<String, Category, Logic> result = HashBasedTable.create();
		for (final String line2 : Util.readFile(file)) {
			final int commentIndex = line2.indexOf("//");
			final String line = (commentIndex > -1 ? line2.substring(0, commentIndex) : line2).trim();

			if (line.isEmpty()) {
				continue;
			}
			final String[] fields = line.split("\t+");
			if (fields.length < 2) {
				throw new IllegalArgumentException("Must be at least two tab-separated fields on line: \"" + line2
						+ "\" in file: " + file.getPath());
			}

			final Category category;
			try {
				category = Category.valueOf(fields[0]);
			} catch (final Exception e) {
				throw new IllegalArgumentException("Unable to interpret category: \"" + fields[0] + "\" on line \""
						+ line2 + "\" in file: " + file.getPath());
			}
			final Logic logic;
			try {
				logic = LogicParser.fromString(fields[1], category);
			} catch (final Exception e) {
				throw new IllegalArgumentException("Unable to interpret semantics: \"" + fields[1] + "\" on line \""
						+ line2 + "\" in file: " + file.getPath());
			}

			if (SemanticType.makeFromCategory(category) != logic.getType()) {
				throw new IllegalArgumentException("Mismatch between syntactic and semantic type. " + category
						+ " has type: " + SemanticType.makeFromCategory(category) + " but " + logic + " has type: "
						+ logic.getType());
			}

			if (fields.length == 2) {
				result.put(ALL_WORDS, category, logic);
			} else {
				for (int i = 2; i < fields.length; i++) {
					result.put(fields[i].replaceAll("-", " "), category, logic);
				}
			}
		}

		return result;
	}

	private Logic getManualLexicalEntry(final String word, final Category category) {
		Logic result = manualLexicon.get(word, category);
		if (result == null) {
			result = manualLexicon.get(ALL_WORDS, category);
		}

		// Alpha-reduce in case this lexical entry is used twice in the sentence.
		return result == null ? result : result.alphaReduce();
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
