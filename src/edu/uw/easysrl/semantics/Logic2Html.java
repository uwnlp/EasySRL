package edu.uw.easysrl.semantics;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.uw.easysrl.semantics.ConnectiveSentence.Connective;
import edu.uw.easysrl.semantics.Logic.LogicVisitor;
import edu.uw.easysrl.semantics.Variable.VariableNames;

/**
 * Builds an HTML string for a logical form.
 *
 */
public class Logic2Html {
	public static String getHTML(final Logic logic) {
		final Visitor v = new Visitor();
		logic.accept(v);
		return v.result.toString();
	}

	private static class Visitor implements LogicVisitor {
		private final StringBuilder result = new StringBuilder();
		private final VariableNames varNames = new VariableNames(true);

		// Rename some constants for printing.
		private final Map<String, String> constantMap = new HashMap<>();
		{
			for (int i = 0; i < 6; i++) {
				constantMap.put("ARG" + i, "A" + i);
			}
			constantMap.put("PNC", "GOAL");
		}

		@Override
		public void visit(final AtomicSentence s) {

			s.getPredicate().accept(this);
			result.append("(");
			printList(s.getChildren(), ",");

			result.append(")");
		}

		@Override
		public void visit(final Function s) {

			s.getPredicate().accept(this);
			result.append("(");
			printList(s.getChildren(), ",");

			result.append(")");
		}

		private void printList(final Collection<? extends Logic> list, final String separator) {
			boolean isFirst = true;
			for (final Logic child : list) {
				if (isFirst) {
					isFirst = false;
				} else {
					result.append(separator);
				}

				final boolean bracket = child instanceof ConnectiveSentence;
				if (bracket) {
					result.append("(");
				}

				child.accept(this);
				if (bracket) {
					result.append(")");
				}
			}
		}

		@Override
		public void visit(final ConnectiveSentence s) {
			final String connective = s.getConnective() == Connective.AND ? "&and;"
					: s.getConnective() == Connective.OR ? "&or;" : s.getConnective().toString();

			printList(s.getChildren(), connective);

		}

		@Override
		public void visit(final Constant s) {
			final String mapped = constantMap.get(s.toString());
			result.append(mapped != null ? mapped : s.toString());
		}

		@Override
		public void visit(final QuantifierSentence s) {
			result.append(s.getQuantifier().asHTML());
			s.getVariable().accept(this);
			final Sentence scope = s.getChild();
			final boolean bracket = !(scope instanceof QuantifierSentence);
			if (bracket) {
				result.append("[");
			}
			scope.accept(this);
			if (bracket) {
				result.append("]");
			}
		}

		@Override
		public void visit(final OperatorSentence s) {
			final String operator;
			switch (s.getOperator()) {
			case MIGHT:
				operator = "&loz;";
				break;
			case MUST:
				operator = "&#9633;";
				break;
			case NOT:
				operator = "&not;";
				break;
			default:
				operator = "";
				break;
			}
			result.append(operator);
			final Sentence scope = s.getScope();
			final boolean bracket = scope instanceof ConnectiveSentence;
			if (bracket) {
				result.append("(");
			}
			scope.accept(this);
			if (bracket) {
				result.append(")");
			}
		}

		@Override
		public void visit(final Set s) {
			result.append("{");
			printList(s.getChildren(), ",");

			result.append("}");
		}

		@Override
		public void visit(final SkolemTerm s) {
			result.append("sk");
			result.append("<sub>");
			s.getCondition().accept(this);
			result.append("</sub>");
		}

		@Override
		public void visit(final Variable s) {
			s.toString(result, varNames);
		}

		@Override
		public void visit(final LambdaExpression lambdaExpression) {
			final String lambda = "&lambda;";
			result.append(lambda);
			printList(lambdaExpression.getArguments(), lambda);
			result.append(".");
			lambdaExpression.getStatement().accept(this);
		}
	}
}
