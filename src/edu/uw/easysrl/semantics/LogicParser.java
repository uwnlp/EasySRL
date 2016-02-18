package edu.uw.easysrl.semantics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;

import edu.uw.easysrl.semantics.ConnectiveSentence.Connective;
import edu.uw.easysrl.semantics.OperatorSentence.Operator;
import edu.uw.easysrl.semantics.QuantifierSentence.Quantifier;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.util.Util;

/**
 * Converts strings for logical forms.
 *
 * Example input: #p.\exists e[p(sk(#x.foo(x)& \not bar(x, john)), e)]
 *
 */
public abstract class LogicParser {

	abstract Logic fromString2(String input, Map<String, Variable> nameToVar);

	abstract boolean canApply(String input, Map<String, Variable> nameToVar);

	private static LogicParser VARIABLE_PARSER = new LogicParser() {

		@Override
		Variable fromString2(final String input, final Map<String, Variable> nameToVar) {
			return nameToVar.get(input);
		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			return nameToVar.containsKey(input) && isVariableName(input);
		}

	};

	boolean isAlphaNumeric(final String input) {
		return input.matches("[a-zA-Z0-9_]+");
	}
	
	boolean isVariableName(final String input) {
		return input.matches("[a-zA-Z0-9_]+'*");
	}

	Variable bindVar(final String name, SemanticType type, final Map<String, Variable> nameToVar) {
		Preconditions.checkState(isVariableName(name), "Expected variable name, but found: " + name);
		Preconditions.checkState(!(nameToVar.containsKey(name)), "Variable name already bound: " + name);
		if (type == null) {
			// Guess a semantic type based on the variable name.
			type = name.startsWith("e") ? SemanticType.Ev
					: name.startsWith("p") || name.startsWith("q") ? SemanticType.EtoT : SemanticType.E;
		}

		final Variable result = new Variable(type);
		nameToVar.put(name, result);
		return result;
	}

	void unbindVar(final String name, final Map<String, Variable> nameToVar) {
		Preconditions.checkState((nameToVar.containsKey(name)));
		nameToVar.remove(name);
	}

	private static class LambdaParser extends LogicParser {

		@Override
		Logic fromString2(final String input, final Map<String, Variable> nameToVar) {
			return fromString2(input, null, nameToVar);
		}

		/**
		 * If semanticType!=null, it is the semantic type of the corresponding to logical form. This lets us set the
		 * semantic types on the variables.
		 */
		Logic fromString2(final String input, final SemanticType semanticType, final Map<String, Variable> nameToVar) {
			final String withoutLambda = input.substring(1);
			final int endVar = Util.findNonNestedChar(withoutLambda, " .#");
			final String name = withoutLambda.substring(0, endVar);

			final SemanticType semanticTypeOfVariable = semanticType != null ? semanticType.getFrom() : null;

			final Variable var = bindVar(name, semanticTypeOfVariable, nameToVar);

			String afterVar = withoutLambda.substring(endVar).trim();
			if (afterVar.startsWith(".")) {
				afterVar = afterVar.substring(1).trim();
			}

			final Logic argument;
			if (canApply(afterVar, nameToVar)) {
				Preconditions
				.checkState(semanticType.isComplex(), "Mismatch between type of category and logical form");
				argument = fromString2(afterVar, semanticType.getTo(), nameToVar);
			} else {
				Preconditions.checkState(semanticType == null || !semanticType.getTo().isComplex(),
						"Mismatch between type of category and logical form");
				argument = parse(afterVar, nameToVar);
			}

			unbindVar(name, nameToVar);
			return LambdaExpression.make(argument, var);
		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			return input.startsWith("#");
		}

	};

	private static LambdaParser LAMBDA_EXPRESSION_PARSER = new LambdaParser();

	private static LogicParser CONSTANT_PARSER = new LogicParser() {

		@Override
		Logic fromString2(final String input, final Map<String, Variable> nameToVar) {
			return new Constant(input, SemanticType.E);
		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			return isAlphaNumeric(input);
		}

	};

	private static LogicParser OPERATOR_SENTENCE_PARSER = new LogicParser() {

		@Override
		Logic fromString2(final String input, final Map<String, Variable> nameToVar) {
			final String opString = getOpString(input);
			final Operator op = getOp(opString);
			return new OperatorSentence(op, (Sentence) parse(input.substring(opString.length()), nameToVar));

		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			return getOpString(input) != null;
		}

		private String getOpString(String input) {
			for (final Operator op : OperatorSentence.Operator.values()) {
				// \not p(x)
				if (input.startsWith("\\")
						&& input.substring(1, op.toString().length() + 1).equalsIgnoreCase(op.toString())) {
					return input.substring(0, op.toString().length() + 1);
				} else if (input.startsWith(op.asString())) {
					return op.asString();
				}
			}
			return null;
		}
		
		private Operator getOp(final String opString) {
			for (final Operator op : OperatorSentence.Operator.values()) {
				// \not p(x)
				if (opString.equalsIgnoreCase("\\" + op.toString()) || opString.equals(op.asString())) {
					return op;
				}
			}
			return null;
		}

	};
	private static LogicParser QUANTIFIER_SENTENCE_PARSER = new LogicParser() {
		// \exists x[p(x)]

		@Override
		Logic fromString2(final String input, final Map<String, Variable> nameToVar) {
			final String opString = getOpString(input);
			final Quantifier quantifier = getOp(opString);
			final String afterQuantifier = input.substring(opString.length()).trim();
			final int bracket = afterQuantifier.indexOf("[");
			Preconditions.checkState(bracket > -1);
			final String name = afterQuantifier.substring(0, bracket);
			final Variable var = bindVar(name, null, nameToVar);

			final Logic result = new QuantifierSentence(quantifier, var, (Sentence) parse(
					afterQuantifier.substring(bracket + 1, afterQuantifier.length() - 1), nameToVar));
			unbindVar(name, nameToVar);
			return result;

		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			return getOpString(input) != null;
		}

		private Quantifier getOp(final String opString) {
			for (final Quantifier op : Quantifier.values()) {
				if (opString.equals(op.getSymbol()) || opString.equals("\\" + op.toString())) {
					return op;
				}
			}
			return null;
		}
		
		private String getOpString(final String input) {
			if (!input.endsWith("]")) {
				return null;
			}
			for (final Quantifier op : Quantifier.values()) {
				if (// \exists
						(input.startsWith("\\") && input.length() > op.toString().length() && input
								.substring(1, op.toString().length() + 1).toUpperCase().equals(op.toString()))) {
					return input
							.substring(0, op.toString().length() + 1).toUpperCase();
				} else if (input.startsWith(op.getSymbol())) {
					return op.getSymbol();
				}
			}
			return null;
		}

	};

	private static LogicParser CONNECTIVE_SENTENCE_PARSER = new LogicParser() {

		@Override
		Logic fromString2(final String input, final Map<String, Variable> nameToVar) {
			final int i = Util.findNonNestedChar(input, "&|");
			final Connective c = Connective.fromString(input.substring(i, i + 1));
			final Logic left = parse(input.substring(0, i), nameToVar);
			final Logic right = parse(input.substring(i + 1), nameToVar);

			return ConnectiveSentence.make(c, (Sentence) left, (Sentence) right);
		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			return Util.findNonNestedChar(input, "&|") > -1;
		}
	};

	private static LogicParser ATOMIC_SENTENCE_PARSER = new LogicParser() {

		@Override
		Logic fromString2(final String input, final Map<String, Variable> nameToVar) {
			final int i = input.indexOf("(");
			final String predicateString = input.substring(0, i);
			String argumentsString = input.substring(i + 1, input.length() - 1);
			final List<Logic> arguments = new ArrayList<>();
			int comma = Util.findNonNestedChar(argumentsString, ",");
			while (comma != -1) {
				final String argumentString = argumentsString.substring(0, comma);
				parseArgument(predicateString, argumentString, arguments, nameToVar);

				argumentsString = argumentsString.substring(comma + 1);
				comma = Util.findNonNestedChar(argumentsString, ",");
			}

			parseArgument(predicateString, argumentsString, arguments, nameToVar);

			if (VARIABLE_PARSER.canApply(predicateString, nameToVar)) {
				return new AtomicSentence(VARIABLE_PARSER.fromString2(predicateString, nameToVar), arguments);
			} else if (CONSTANT_PARSER.canApply(predicateString, nameToVar)) {
				return new AtomicSentence(predicateString, arguments);
			} else {
				throw new IllegalArgumentException("Expected predicate: " + predicateString);
			}
		}

		private void parseArgument(final String predicateString, final String argumentString,
				final List<Logic> arguments, final Map<String, Variable> nameToVar) {
			if (LAMBDA_EXPRESSION_PARSER.canApply(argumentString, nameToVar) && arguments.size() == 0) {
				// Allow nested lambda expressions: p(#x#e. sk(#z.foo(x,z,e),y,z))
				// Useful for really weird categories taking higher-order arguments.
				final Logic p = VARIABLE_PARSER.fromString2(predicateString, nameToVar);
				arguments.add(LAMBDA_EXPRESSION_PARSER.fromString2(argumentString, p.getType().getFrom(), nameToVar));
			} else {
				arguments.add(parse(argumentString, nameToVar));
			}
		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			final int i = input.indexOf("(");
			if (i == -1 || Util.findClosingBracket(input, i) != input.length() - 1) {
				return false;
			}

			return CONSTANT_PARSER.canApply(input.substring(0, i), nameToVar);
		}
	};

	private static LogicParser BRACKETS_PARSER = new LogicParser() {

		@Override
		Logic fromString2(final String input, final Map<String, Variable> nameToVar) {
			return parse(input.substring(1, input.length() - 1), nameToVar);
		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			return (input.startsWith("(") && Util.findClosingBracket(input, 0) == input.length() - 1);
		}

	};

	private static LogicParser SKOLEM_PARSER = new LogicParser() {

		@Override
		Logic fromString2(final String input, final Map<String, Variable> nameToVar) {
			final Logic child = parse(input.substring("sk".length()), nameToVar);
			// Check child is a lambda expression
			Preconditions.checkArgument(child.getArguments().size() > 0,
					"Skolem terms should take lambda expression argument: " + input);
			return new SkolemTerm((LambdaExpression) child);
		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			return input.startsWith("sk(") && Util.findClosingBracket(input, "sk(".length() - 1) == input.length() - 1;
		}
	};
	
	private static LogicParser SET_PARSER = new LogicParser() {

		@Override
		Logic fromString2(final String input, final Map<String, Variable> nameToVar) {
			String argumentsString = input.substring(1, input.length() - 1);
			final List<Logic> arguments = new ArrayList<>();
			int comma = Util.findNonNestedChar(argumentsString, ",");
			while (comma != -1) {
				final String argumentString = argumentsString.substring(0, comma);
				arguments.add(parse(argumentString, nameToVar));

				argumentsString = argumentsString.substring(comma + 1);
				comma = Util.findNonNestedChar(argumentsString, ",");
			}
			arguments.add(parse(argumentsString, nameToVar));
			
			Logic setExpression = new Set(arguments);
			
			return setExpression;
		}

		@Override
		boolean canApply(final String input, final Map<String, Variable> nameToVar) {
			return input.startsWith("{") && input.endsWith("}");
		}
		
	};

	// This list defines precedence
	private final static List<LogicParser> parsers = Arrays.asList(BRACKETS_PARSER, LAMBDA_EXPRESSION_PARSER,
			CONNECTIVE_SENTENCE_PARSER, SKOLEM_PARSER, QUANTIFIER_SENTENCE_PARSER, OPERATOR_SENTENCE_PARSER,
			ATOMIC_SENTENCE_PARSER, VARIABLE_PARSER, CONSTANT_PARSER, SET_PARSER);

	public static Logic fromString(final String input, final Category category) {
		return fromString(input, category, new HashMap<String, Variable>());
	}

	private static Logic fromString(final String input, final Category category, final Map<String, Variable> nameToVar) {
		if (category != null && LAMBDA_EXPRESSION_PARSER.canApply(input, nameToVar)) {
			// If we have a category and this is lambda expression, we can sort out the semantic types of the variables.
			return LAMBDA_EXPRESSION_PARSER.fromString2(input, SemanticType.makeFromCategory(category), nameToVar);
		} else {
			return parse(input, nameToVar);
		}
	}

	private static Logic parse(String input, final Map<String, Variable> nameToVar) {
		input = input.trim();
		for (final LogicParser parser : parsers) {
			if (parser.canApply(input, nameToVar)) {
				return parser.fromString2(input, nameToVar);
			}
		}

		throw new IllegalArgumentException("Unable to build logic for string: " + input);

	}

}
