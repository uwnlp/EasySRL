package edu.uw.easysrl.syntax.grammar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.semantics.AtomicSentence;
import edu.uw.easysrl.semantics.ConnectiveSentence;
import edu.uw.easysrl.semantics.ConnectiveSentence.Connective;
import edu.uw.easysrl.semantics.Function;
import edu.uw.easysrl.semantics.LambdaExpression;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.LogicParser;
import edu.uw.easysrl.semantics.SemanticType;
import edu.uw.easysrl.semantics.Sentence;
import edu.uw.easysrl.semantics.Set;
import edu.uw.easysrl.semantics.Variable;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.util.Util;

public abstract class Combinator {
	public enum RuleType {
		FA(RuleClass.OTHER), BA(RuleClass.OTHER), FC(RuleClass.FC), BX(RuleClass.BX), GFC(RuleClass.GFC), GBX(
				RuleClass.GBX), CONJ(RuleClass.CONJ), RP(RuleClass.RP), LP(RuleClass.LP), NOISE(RuleClass.OTHER), FORWARD_TYPERAISE(
						RuleClass.FORWARD_TYPERAISE), BACKWARD_TYPE_RAISE(RuleClass.BACKWARD_TYPE_RAISE), TYPE_CHANGE(
								RuleClass.OTHER), LEXICON(RuleClass.LEXICON);
		private final RuleClass ruleClass;

		RuleType(final RuleClass ruleClass) {
			this.ruleClass = ruleClass;
		}

		public RuleClass getNormalFormClassForRule() {
			return ruleClass;
		}

	}

	public enum RuleClass {
		FC, BX, GFC, GBX, FORWARD_TYPERAISE, BACKWARD_TYPE_RAISE, CONJ, LEXICON, OTHER, LP, RP,
		// Forward and backward modifiers, e.g. N/N N --> N
		F_MOD, B_MOD
	}

	// TODO Can we get rid of RuleType altogether?
	private final static Map<RuleType, Combinator> typeToCombinator = new HashMap<>();

	private Combinator(final RuleType ruleType) {
		this.ruleType = ruleType;
		typeToCombinator.put(ruleType, this);
	}

	public static Combinator fromRule(final RuleType ruleType) {
		return typeToCombinator.get(ruleType);
	}

	public static class RuleProduction {
		private RuleProduction(final RuleType ruleType, final Category result, final boolean headIsLeft,
				final Combinator combinator) {
			this.ruleType = ruleType;
			this.category = result;
			this.headIsLeft = headIsLeft;
			this.combinator = combinator;
		}

		private final RuleType ruleType;
		private final Category category;
		private final boolean headIsLeft;
		private final Combinator combinator;

		public RuleType getRuleType() {
			return ruleType;
		}

		public Category getCategory() {
			return category;
		}

		public boolean isHeadIsLeft() {
			return headIsLeft;
		}

		public Combinator getCombinator() {
			return combinator;
		}
	}

	public abstract boolean headIsLeft(Category left, Category right);

	public final static Collection<Combinator> STANDARD_COMBINATORS = new ArrayList<>(Arrays.asList(
			new ForwardApplication(), new BackwardApplication(),
			new ForwardComposition(Slash.FWD, Slash.FWD, Slash.FWD), new BackwardComposition(Slash.FWD, Slash.BWD,
					Slash.FWD), new GeneralizedForwardComposition(Slash.FWD, Slash.FWD, Slash.FWD),
					new GeneralizedBackwardComposition(Slash.FWD, Slash.BWD, Slash.FWD), new Conjunction(),
					new RemovePunctuation(false), new RemovePunctuation(true)// , new CommaAndVPtoNPmodifier() // TODO
			, new CommaAndVerbPhrasetoAdverb(), new ParentheticalDirectSpeech()
	// TODO
			));

	public static Collection<Combinator> loadSpecialCombinators(final File file) throws IOException {
		final Collection<Combinator> newCombinators = new ArrayList<>();
		for (String line : Util.readFile(file)) {
			// l , S[to]\NP NP\NP
			if (line.indexOf("#") > -1) {
				line = line.substring(0, line.indexOf("#"));
			}

			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}

			final String[] fields = line.split(" ");
			final boolean headIsLeft = fields[0].equals("l");
			final Category left = Category.valueOf(fields[1]);
			final Category right = Category.valueOf(fields[2]);
			final Category result = Category.valueOf(fields[3]);
			newCombinators.add(new SpecialCombinator(left, right, result, headIsLeft));
		}
		return newCombinators;
	}

	private final RuleType ruleType;

	public abstract boolean canApply(Category left, Category right);

	public abstract Category apply(Category left, Category right);

	public abstract Logic apply(Logic left, Logic right);

	/**
	 * Makes sure wildcard features are correctly instantiated.
	 *
	 * We want: S[X]/(S[X]\NP) and S[dcl]\NP to combine to S[dcl]. This is done by finding any wildcards that need to be
	 * matched between S[X]\NP and S[dcl]\NP, and applying the substitution to S[dcl].
	 */
	private static Category correctWildCardFeatures(final Category toCorrect, final Category match1,
			final Category match2) {
		return toCorrect.doSubstitution(match1.getSubstitution(match2));
	}

	/**
	 * Returns a set of rules that can be applied to a pair of categories.
	 */
	public static List<RuleProduction> getRules(Category left, Category right, final Collection<Combinator> rules) {
		// [nb] feature is not helpful.
		left = Category.valueOf(left.toString().replace("[nb]", ""));
		right = Category.valueOf(right.toString().replace("[nb]", ""));

		final List<RuleProduction> result = new ArrayList<>(2);
		for (final Combinator c : rules) {
			if (c.canApply(left, right)) {
				result.add(new RuleProduction(c.ruleType, c.apply(left, right), c.headIsLeft(left, right), c));
			}
		}

		if (result.size() == 0) {
			return Collections.emptyList();
		}
		
		return result;
	}

	private static class CommaAndVPtoNPmodifier extends Combinator {
		private final Collection<Category> verbPhrases = new HashSet<>(Arrays.asList(Category.valueOf("S[ng]\\NP"), // PV
				// ,
				// whistling
				// ,
				// walked home
				Category.valueOf("S[pss]\\NP"),// PV , tired, went to
				// bed ???
				Category.valueOf("S[adj]\\NP") // PV, 59 years old,
				, Category.valueOf("S[to]\\NP")));
		private final Category result = Category.valueOf("NP\\NP");
		private final Logic semantics = LogicParser.fromString("#p#y . sk(#x . eq(x, y) & \\exists e[p(x, e)])",
				Category.valueOf("(NP\\NP)/(S\\NP)"));

		private final DependencyStructure dependencyStructure = DependencyStructure.makeUnaryRuleTransformation(
				"(S_2\\NP_1)_2", "(NP_1\\NP_1)_2");

		private CommaAndVPtoNPmodifier() {
			super(RuleType.NOISE);
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			return false;
		}

		@Override
		public boolean canApply(final Category left, final Category right) {
			return left == Category.COMMA && verbPhrases.contains(right);
		}

		@Override
		public Category apply(final Category left, final Category right) {
			return result;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return dependencyStructure.apply(right, resolvedDependencies);
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return semantics.alphaReduce().apply(right);
		}
	}

	private static class Conjunction extends Combinator {

		private Conjunction() {
			super(RuleType.CONJ);
		}

		@Override
		public boolean canApply(final Category left, final Category right) {
			if (Category.valueOf("NP\\NP").matches(right)) {
				// C&C evaluation script doesn't let you do this, for some
				// reason.
				return false;
			}

			return (left == Category.CONJ || left == Category.COMMA || left == Category.SEMICOLON)
					&& !right.isPunctuation() // Don't start making weird ,\,
					// categories...
					&& !right.isTypeRaised() // Improves coverage of C&C
					// evaluation script. Categories
					// can just conjoin first, then
					// type-raise.

					// Blocks noun conjunctions, which should normally be NP
					// conjunctions.
					// In a better world, conjunctions would have categories
					// like (NP\NP/NP.
					// Doesn't affect F-scopes, but makes output semantically
					// nicer.
					&& !(!right.isFunctor() && right.getType().equals("N"));

		}

		@Override
		public Category apply(final Category left, final Category right) {
			return Category.make(right, Slash.BWD, right);
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			return false;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return right.conjunction();
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			if (right.getArguments().size() == 0) {
				// NP conjunctions
				final Variable x = new Variable(right.getType());
				return LambdaExpression.make(new Set(x, right), x);
			} else if (right.getType().isFunctionInto(SemanticType.E)) {
				// Other sets
				final Variable f = new Variable(right.getType());
				final List<Variable> vars = new ArrayList<>();
				vars.add(f);
				vars.addAll(right.getArguments());
				return LambdaExpression.make(
						new Set(Function.make(f, right.getArguments()), ((LambdaExpression) right).getStatement()),
						vars);
			}

			else {
				// Boolean conjunctions
				// conj + S\NP
				// conj + #x#e.foo(x,e) --> #p#x#e . foo(x,e) & p(x,e)
				final Variable p = new Variable(right.getType());
				final List<Variable> vars = new ArrayList<>();
				// TODO very hacky. Move this to the lexicon.
				final Connective connective = left.toString().equals("or") ? Connective.OR : Connective.AND;
				vars.add(p);
				vars.addAll(right.getArguments());
				return LambdaExpression.make(ConnectiveSentence.make(connective,
						new AtomicSentence(p, right.getArguments()),
						(Sentence) ((LambdaExpression) right).getStatement()), vars);
			}
		}
	}

	private static class RemovePunctuation extends Combinator {
		private final boolean punctuationIsLeft;

		private RemovePunctuation(final boolean punctuationIsLeft) {
			super(punctuationIsLeft ? RuleType.LP : RuleType.RP);
			this.punctuationIsLeft = punctuationIsLeft;
		}

		@Override
		public boolean canApply(final Category left, final Category right) {
			return punctuationIsLeft ? left.isPunctuation() : right.isPunctuation();
		}

		@Override
		public Category apply(final Category left, final Category right) {
			return punctuationIsLeft ? right : left;
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			return !punctuationIsLeft;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return punctuationIsLeft ? right : left;
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return punctuationIsLeft ? right : left;
		}
	}

	private static class SpecialCombinator extends Combinator {
		private final Category left;
		private final Category right;
		private final Category result;
		private final boolean headIsLeft;

		private SpecialCombinator(final Category left, final Category right, final Category result,
				final boolean headIsLeft) {
			super(RuleType.NOISE);
			this.left = left;
			this.right = right;
			this.result = result;
			this.headIsLeft = headIsLeft;
		}

		@Override
		public boolean canApply(final Category left, final Category right) {
			return this.left.matches(left) && this.right.matches(right);
		}

		@Override
		public Category apply(final Category left, final Category right) {
			return result;
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			return headIsLeft;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			throw new UnsupportedOperationException("TODO?");
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			throw new RuntimeException("TODO");
		}
	}

	private static class ForwardApplication extends Combinator {
		private ForwardApplication() {
			super(RuleType.FA);
		}

		@Override
		public boolean canApply(final Category left, final Category right) {
			return left.isFunctor() && left.getSlash() == Slash.FWD && left.getRight().matches(right);
		}

		@Override
		public Category apply(final Category left, final Category right) {
			if (left.isModifier()) {
				return right;
			}

			Category result = left.getLeft();

			result = correctWildCardFeatures(result, left.getRight(), right);

			return result;
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			if (left.isModifier() || left.isTypeRaised() || left == Category.DETERMINER) {
				return false;
			}
			return true;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return left.apply(right, resolvedDependencies);
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return left.apply(right);
		}
	}

	private static class BackwardApplication extends Combinator {
		private BackwardApplication() {
			super(RuleType.BA);
		}

		private final Category SemConj = Category.valueOf("S[em]\\S[em]");
		private final Category sentenceModifier = Category.valueOf("S[dcl]\\S[dcl]");

		@Override
		public boolean canApply(final Category left, final Category right) {
			if (right == SemConj && left == Category.Sdcl) {
				// Hack special case for when we have: "X said he'll win and that she'll lose". Training data has the
				// first conjunct as S[dcl] and the second as S[em]
				return true;
			}

			return right.isFunctor() && right.getSlash() == Slash.BWD && (right.getRight().matches(left));
		}

		@Override
		public Category apply(final Category left, final Category right) {
			Category result;
			if (right.isModifier()) {
				result = left;
			} else {
				result = right.getLeft();
			}

			return correctWildCardFeatures(result, right.getRight(), left);
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			if ((right.isModifier() || right.isTypeRaised()) && right != sentenceModifier) {// S[dcl]\S[dcl] is normally
				// a verb like 'said', which
				// should be the head
				return true;
			}
			return false;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return right.apply(left, resolvedDependencies);
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return right.apply(left);
		}
	}

	private static class ForwardComposition extends Combinator {
		private final Slash leftSlash;
		private final Slash rightSlash;
		private final Slash resultSlash;

		private ForwardComposition(final Slash left, final Slash right, final Slash result) {
			super(RuleType.FC);
			this.leftSlash = left;
			this.rightSlash = right;
			this.resultSlash = result;
		}

		@Override
		public boolean canApply(final Category left, final Category right) {
			return left.isFunctor() && right.isFunctor() && left.getRight().matches(right.getLeft())
					&& left.getSlash() == leftSlash && right.getSlash() == rightSlash;
		}

		@Override
		public Category apply(final Category left, final Category right) {
			Category result;
			if (left.isModifier()) {
				result = right;
			} else {
				result = Category.make(left.getLeft(), resultSlash, right.getRight());
			}

			return correctWildCardFeatures(result, right.getLeft(), left.getRight());
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			if (left.isModifier() || left.isTypeRaised()) {
				return false;
			}
			return true;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return left.compose(right, resolvedDependencies);
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return left.compose(right);
		}
	}

	private static class GeneralizedForwardComposition extends Combinator {
		private final Slash leftSlash;
		private final Slash rightSlash;
		private final Slash resultSlash;

		private GeneralizedForwardComposition(final Slash left, final Slash right, final Slash result) {
			super(RuleType.GFC);
			this.leftSlash = left;
			this.rightSlash = right;
			this.resultSlash = result;
		}

		@Override
		public boolean canApply(final Category left, final Category right) {
			if (left.isFunctor() && right.isFunctor() && right.getLeft().isFunctor()) {
				final Category rightLeft = right.getLeft();
				return left.getRight().matches(rightLeft.getLeft()) && left.getSlash() == leftSlash
						&& rightLeft.getSlash() == rightSlash;
			} else {
				return false;
			}
		}

		@Override
		public Category apply(final Category left, final Category right) {
			if (left.isModifier()) {
				return right;
			}

			final Category rightLeft = right.getLeft();

			Category result = Category.make(Category.make(left.getLeft(), resultSlash, rightLeft.getRight()),
					right.getSlash(), right.getRight());

			result = correctWildCardFeatures(result, rightLeft.getLeft(), left.getRight());
			return result;
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			if (left.isModifier() || left.isTypeRaised()) {
				return false;
			}
			return true;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return left.compose2(right, resolvedDependencies);
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return left.compose2(right);
		}
	}

	private static class GeneralizedBackwardComposition extends Combinator {
		private final Slash leftSlash;
		private final Slash rightSlash;
		private final Slash resultSlash;

		private GeneralizedBackwardComposition(final Slash left, final Slash right, final Slash result) {
			super(RuleType.GBX);
			this.leftSlash = left;
			this.rightSlash = right;
			this.resultSlash = result;
		}

		@Override
		public boolean canApply(final Category left, final Category right) {
			if (left.isFunctor() && right.isFunctor() && left.getLeft().isFunctor()) {
				final Category leftLeft = left.getLeft();
				return right.getRight().matches(leftLeft.getLeft()) && leftLeft.getSlash() == leftSlash
						&& right.getSlash() == rightSlash && !(left.getLeft().isNounOrNP()); // Additional
				// constraint from
				// Steedman (2000)
			} else {
				return false;
			}
		}

		@Override
		public Category apply(final Category left, final Category right) {
			if (right.isModifier()) {
				return left;
			}

			final Category leftLeft = left.getLeft();

			Category result = Category.make(Category.make(right.getLeft(), resultSlash, leftLeft.getRight()),
					left.getSlash(), left.getRight());

			result = correctWildCardFeatures(result, leftLeft.getLeft(), right.getRight());
			return result;
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			if (right.isModifier() || right.isTypeRaised()) {
				return true;
			}
			return false;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return right.compose2(left, resolvedDependencies);
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return right.compose2(left);
		}
	}

	/**
	 * Using for emulating the CCGbank analysis of sentences like "She is, I think, a genius" 
	 */
	private static class ParentheticalDirectSpeech extends Combinator {

		private ParentheticalDirectSpeech() {
			super(RuleType.NOISE);
		}

		private final Logic semantics = LogicParser.fromString("#p#q#x#e . q(x,e) & p(x,e)",
				Category.make(Category.ADVERB, Slash.FWD, Category.valueOf("S/S")));

		private final DependencyStructure dependencyStructure = DependencyStructure.makeUnaryRuleTransformation(
				"(S_3/S_3)_2", "((S_3\\NP_1)_3/(S_3\\NP_1)_3)_2");

		@Override
		public boolean canApply(final Category left, final Category right) {

			return left == Category.COMMA && (right == Category.valueOf("S[dcl]/S[dcl]"));

		}

		@Override
		public Category apply(final Category left, final Category right) {
			return Category.valueOf("(S\\NP)/(S\\NP)");
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			return false;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return dependencyStructure.apply(right, resolvedDependencies);
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return semantics.alphaReduce().apply(right);
		}
	}

	private static class CommaAndVerbPhrasetoAdverb extends Combinator {
		// He walked , thinking about cake

		private CommaAndVerbPhrasetoAdverb() {
			super(RuleType.NOISE);
		}

		private final Category ngVP = Category.valueOf("S[ng]\\NP");
		private final Category pssVP = Category.valueOf("S[pss]\\NP");
		private final Logic semantics = LogicParser.fromString("#p#q#x#e . q(x,e) & \\exists e1[p(x,e1) & ARG(e, e1)]",
				Category.make(Category.ADVERB, Slash.FWD, ngVP));

		private final DependencyStructure dependencyStructure = DependencyStructure.makeUnaryRuleTransformation(
				"(S_2\\NP_1)_2", "((S_3\\NP_1)_3\\(S_3\\NP_1)_3)_2");

		@Override
		public boolean canApply(final Category left, final Category right) {

			return left == Category.COMMA && (right == ngVP || right == pssVP);

		}

		@Override
		public Category apply(final Category left, final Category right) {
			return Category.ADVERB;
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			return false;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return dependencyStructure.apply(right, resolvedDependencies);
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return semantics.alphaReduce().apply(right);
		}
	}

	private static class BackwardComposition extends Combinator {
		private final Slash leftSlash;
		private final Slash rightSlash;
		private final Slash resultSlash;

		private BackwardComposition(final Slash left, final Slash right, final Slash result) {
			super(RuleType.BX);
			this.leftSlash = left;
			this.rightSlash = right;
			this.resultSlash = result;
		}

		@Override
		public boolean canApply(final Category left, final Category right) {
			return left.isFunctor() && right.isFunctor() && right.getRight().matches(left.getLeft())
					&& left.getSlash() == leftSlash && right.getSlash() == rightSlash && !(left.getLeft().isNounOrNP()); // Additional
			// constraint
			// from Steedman (2000)
		}

		@Override
		public Category apply(final Category left, final Category right) {
			Category result;
			if (right.isModifier()) {
				result = left;
			} else {
				result = Category.make(right.getLeft(), resultSlash, left.getRight());
			}

			return result.doSubstitution(left.getLeft().getSubstitution(right.getRight()));
		}

		@Override
		public boolean headIsLeft(final Category left, final Category right) {
			if (right.isModifier() || right.isTypeRaised()) {
				return true;
			}
			return false;
		}

		@Override
		public DependencyStructure apply(final DependencyStructure left, final DependencyStructure right,
				final List<UnlabelledDependency> resolvedDependencies) {
			return right.compose(left, resolvedDependencies);
		}

		@Override
		public Logic apply(final Logic left, final Logic right) {
			return right.compose(left);
		}
	}

	public RuleType getRuleType() {
		return ruleType;
	}

	public abstract DependencyStructure apply(DependencyStructure left, DependencyStructure right,
			List<UnlabelledDependency> resolvedDependencies);
}
