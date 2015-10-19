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
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.util.Util;

/**
 * Builds logical forms for words, based on their categories and semantic dependencies.
 *
 */
public class Lexicon {
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

		final List<SRLLabel> labels;
		if (parse.isPresent()) {
			final List<ResolvedDependency> deps = parse.get().getOrderedDependenciesAtPredicateIndex(wordIndex);
			for (final ResolvedDependency dep : deps) {
				if (dep != null && dep.getCategory().getArgument(dep.getArgNumber()) == Category.PR) {
					// Merge predicates in verb-particle constructions, e.g. pick_up
					lemma = lemma + "_" + parse.get().getLeaf(dep.getArgumentIndex()).getWord();
				}
			}

			// Find the semantic role for each argument of the category.
			labels = deps.stream().map(x -> x == null ? null : x.getSemanticRole()).collect(Collectors.toList());
		} else {
			labels = noLabels;
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

		if (Category.valueOf("conj|conj").matches(category)) {
			// Special case, since we have other rules to deal with this non-compositionally.
			return new Constant(lemma, SemanticType.E);
		}

		return LambdaExpression.make(
				getEntry(lemma, category, coindexation, vars, head, null, idToVar, isContentWord(lemma, pos, category),
						labels), vars);
	}

	private boolean isContentWord(final String word, final String pos, final Category category) {
		if (!(pos.length() > 1 && contentPOS.contains(pos.substring(0, 2)))) {
			return false;
		} else if (Category.valueOf("(S\\NP)/(S\\NP)").matches(category) && auxiliaryVerbs.contains(word)) {
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

		// A label for this argument, either a semantic role or "ARG".
		final String argumentLabel = (label == SRLFrame.NONE) ? ARG : label.toString();

		if (category.getNumberOfArguments() == 0) {
			// Base case. N, S, NP etc.
			if (category.equals(Category.N) || Category.S.matches(category.getArgument(0))) {
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

		} else if (category.isModifier() && coindexation.isModifier()) {
			// Modifier categories.
			// (S\NP)/(S\NP) --> #p#x#e . lemma(e) & p(x,e)
			// S/S --> #p#e . lemma(e) & p(e)
			// (S/S)/(S/S) --> #p#q#e . lemma(e) & p(e) & q(p, e)
			final Sentence modifier;
			final AtomicSentence px = new AtomicSentence(vars.get(0), vars.subList(1, vars.size()));
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
			if (argument.getNumberOfArguments() == 0) {
				// Atomic argument, e.g. S\NP
				if (argument.equals(Category.NP) || argument.equals(Category.PP)) {
					if (category.isFunctionIntoModifier()) {
						// No semantic role --> on:(S\S)/NP --> #x#p#e . p(e) & on(x,e)
						// With semantic role --> on:(S\S)/NP --> #x#p#e . p(e) & TMP(x,e)
						resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(
								label == SRLFrame.NONE ? word : argumentLabel, vars.get(0), head), resultSoFar);

					} else {
						// (S\NP)/NP --> ... & arg(y, x)
						resultForArgument = isContentWord ? ConnectiveSentence.make(Connective.AND, new AtomicSentence(
								argumentLabel, vars.get(0), head), resultSoFar) : resultSoFar;
					}
				} else if (Category.N.matches(argument)) {
					// NP/N --> ... & p(x)
					resultForArgument = ConnectiveSentence.make(Connective.AND, new AtomicSentence(vars.get(0), head),
							resultSoFar);
				} else if (argument.equals(Category.PR) || argument.equals(Category.NPthr)
						|| argument.equals(Category.NPexpl)) {
					// Semantically vacuous arguments
					resultForArgument = resultSoFar;
				} else if (Category.S.matches(argument)) {
					// N/S --> ... & p(ev)
					final Variable ev = new Variable(SemanticType.Ev);
					resultForArgument = ConnectiveSentence.make(
							Connective.AND,
							new QuantifierSentence(Quantifier.EXISTS, ev, ConnectiveSentence.make(Connective.AND,
									new AtomicSentence(vars.get(0), ev), new AtomicSentence(argumentLabel, head, ev))),
									resultSoFar);
				} else {
					throw new IllegalStateException();
				}

			} else if (Category.valueOf("NP|NP").matches(argument) || Category.valueOf("NP|N").matches(argument)
					|| Category.valueOf("(N\\N)/NP").matches(argument)) {
				// Very rare and weird (NP/NP) arguments. Discard these.
				resultForArgument = resultSoFar;
			} else {
				// Complex argument, e.g. ((S[dcl]\NP)/NP_1)/(S[to]\NP_1)

				// Build up a list of the arguments of the argument. If these arguments aren't supplied elsewhere, we
				// need to quantify them.
				final List<Variable> argumentsOfArgument = new ArrayList<>(argument.getNumberOfArguments());
				final List<Variable> toQuantify = new ArrayList<>();
				Coindexation coindexationOfArgument = coindexation.getRight();
				for (int i = argument.getNumberOfArguments(); i > 0; i--) {
					// Iterate over arguments of argument.
					Variable var;
					final IDorHead id = coindexationOfArgument.getRight().getID();
					if (idToVar.containsKey(id)) {
						// Argument is co-indexed with another argument.
						var = idToVar.get(id);
					} else {
						// Not coindexed - make a new variable, and quantify it later.
						final Category argumentOfArgument = argument.getArgument(i);
						var = new Variable(SemanticType.makeFromCategory(argumentOfArgument));
						toQuantify.add(var);
					}
					argumentsOfArgument.add(var);
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

				Sentence argumentSemantics = new AtomicSentence(vars.get(0), argumentsOfArgument);
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
			}

			// Recursively build up the next interpretation for the other arguments.
			return getEntry(word, category.getLeft(), coindexation.getLeft(), vars.subList(1, vars.size()), head,
					resultForArgument, idToVar, isContentWord, labels.subList(0, labels.size() - 1));
		}
	}

	public boolean isMultiWordExpression(final SyntaxTreeNode node) {
		if (node.getLeaves().stream().allMatch(x -> x.getPos().startsWith("NNP"))) {
			// Analyze "Barack Obama" as barack_obama, not #x.barack(x)&obama(x)
			return true;
		} else if (node.getCategory() == Category.CONJ) {
			// Don't bother trying to do multi-word conjunctions compositionally (e.g. "as well as").
			return true;
		} else if (getManualLexicalEntry(node.getWord(), node.getCategory()) != null) {
			// Multiword expressions
			return true;
		}
		return false;
	}

	private static Table<String, Category, Logic> loadLexicon(final File file) throws IOException {
		final Table<String, Category, Logic> result = HashBasedTable.create();
		for (final String line2 : Util.readFile(file)) {
			final int commentIndex = line2.indexOf("//");
			final String line = commentIndex > -1 ? line2.substring(0, commentIndex).trim() : line2;

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

		return result;
	}
}
