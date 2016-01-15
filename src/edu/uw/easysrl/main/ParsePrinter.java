package edu.uw.easysrl.main;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.lemmatizer.MorphaStemmer;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.Logic2Html;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLabelling;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeVisitor;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.util.Util;

public abstract class ParsePrinter {
	public final static CCGBankPrinter CCGBANK_PRINTER = new CCGBankPrinter();
	public final static ParsePrinter HTML_PRINTER = new HTMLPrinter();
	public final static ParsePrinter PROLOG_PRINTER = new PrologPrinter();
	public final static ParsePrinter EXTENDED_CCGBANK_PRINTER = new ExtendedCCGBankPrinter();
	public final static ParsePrinter SUPERTAG_PRINTER = new SupertagPrinter();
	public final static ParsePrinter SRL_PRINTER = new SRLprinter(false);
	public final static ParsePrinter SRL_PRINTER_WITH_INDICES = new SRLprinter(true);
	public final static ParsePrinter LOGIC_PRINTER = new LogicPrinter();
	public final static ParsePrinter DEPENDENCIES_PRINTER = new DependenciesPrinter();

	public String print(final List<SyntaxTreeNode> parses, final int id) {
		final StringBuilder result = new StringBuilder();

		if (parses == null) {
			if (id > -1) {
				printHeader(id, result);
			}
			printFailure(result);
		} else {
			boolean isFirst = true;
			for (final SyntaxTreeNode parse : parses) {
				if (isFirst) {
					isFirst = false;
				} else {
					// Separate N-best lists
					result.append("\n");
				}
				if (id > -1) {
					printHeader(id, result);
				}
				printParse(parse, id, result);
			}
		}

		printFooter(result);
		return result.toString();
	}

	public String print(final SyntaxTreeNode entry, final int id) {
		final StringBuilder result = new StringBuilder();
		if (id > -1) {
			printHeader(id, result);
		}

		if (entry == null) {
			printFailure(result);
		} else {
			printParse(entry, id, result);
		}

		printFooter(result);
		return result.toString();
	}

	protected abstract void printFileHeader(StringBuilder result);

	protected abstract void printFailure(StringBuilder result);

	protected abstract void printHeader(int id, StringBuilder result);

	protected abstract void printFooter(StringBuilder result);

	protected abstract void printParse(SyntaxTreeNode parse, int sentenceNumber, StringBuilder result);

	private abstract static class ParsePrinterVisitor implements SyntaxTreeNodeVisitor {
		final StringBuilder result;

		private ParsePrinterVisitor(final StringBuilder result) {
			this.result = result;
		}

	}

	public static class CCGBankPrinter extends ParsePrinter {

		@Override
		protected void printFailure(final StringBuilder result) {
			result.append("(<L NP NN NN fail N>)");
		}

		class CCGBankParsePrinterVisitor extends ParsePrinterVisitor {
			// (<T S[dcl] 0 2> (<T S[dcl] 1 2> (<T NP 0 2> (<T NP 0 2> (<T NP 0
			// 2> (<T NP 0 1> (<T N 1 2> (<L N/N NNP NNP Pierre N_73/N_73>) (<L
			// N NNP NNP Vinken N>) ) ) (<L , , , , ,>) )

			CCGBankParsePrinterVisitor(final StringBuilder result) {
				super(result);
			}

			@Override
			public void visit(final SyntaxTreeNodeBinary node) {
				result.append("(<T ");
				result.append(node.getCategory().toString());
				result.append(" " + (node.isHeadIsLeft() ? "0" : "1") + " 2> ");
				node.getLeftChild().accept(this);
				node.getRightChild().accept(this);

				result.append(") ");

			}

			@Override
			public void visit(final SyntaxTreeNodeUnary node) {
				result.append("(<T ");
				result.append(node.getCategory().toString());
				result.append(" 0 1> ");
				node.getChild().accept(this);
				result.append(") ");
			}

			@Override
			public void visit(final SyntaxTreeNodeLeaf node) {
				result.append("(<L ");
				result.append(node.getCategory().toString());
				if (node.getPos() == null) {
					result.append(" POS POS ");
				} else {
					result.append(" " + node.getPos() + " " + node.getPos() + " ");
				}
				result.append(normalize(node.getWord()));
				result.append(" ");
				result.append(node.getCategory().toString());
				result.append(">) ");
			}

			@Override
			public void visit(final SyntaxTreeNodeLabelling node) {
				node.getChild(0).accept(this);
			}
		}

		/**
		 * Normalizes words - e.g. converting brackets to CCGBank format.
		 */
		private static String normalize(final String word) {
			if (word.length() > 1) {
				return word;
			} else if (word.equals("{")) {
				return "-LRB-";
			} else if (word.equals("}")) {
				return "-RRB-";
			} else if (word.equals("(")) {
				return "-LRB-";
			} else if (word.equals(")")) {
				return "-RRB-";
			}
			return word;
		}

		@Override
		protected void printHeader(final int id, final StringBuilder result) {
			result.append("ID=" + id + "\n");
		}

		@Override
		protected void printFooter(final StringBuilder result) {
		}

		@Override
		protected void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			printParse(parse, result);
		}

		protected void printParse(final SyntaxTreeNode parse, final StringBuilder result) {
			parse.accept(new CCGBankParsePrinterVisitor(result));
		}

		@Override
		protected void printFileHeader(final StringBuilder result) {
		}

		public String print(final SyntaxTreeNode syntaxTreeNode) {
			final StringBuilder result = new StringBuilder();
			printParse(syntaxTreeNode, result);
			return result.toString();
		}

		@Override
		protected boolean outputsLogic() {
			return false;
		}

		@Override
		protected boolean outputsDependencies() {
			return false;
		}
	}

	private static class SRLprinter extends ParsePrinter {
		private final boolean includeWordIndices;

		public SRLprinter(final boolean includeWordIndices) {
			this.includeWordIndices = includeWordIndices;
		}

		@Override
		protected void printFileHeader(final StringBuilder result) {
		}

		@Override
		protected void printFailure(final StringBuilder result) {
		}

		@Override
		protected void printHeader(final int id, final StringBuilder result) {
		}

		@Override
		protected void printFooter(final StringBuilder result) {
		}

		@Override
		protected void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			final List<ResolvedDependency> deps = parse.getAllLabelledDependencies();
			for (final ResolvedDependency dep : deps) {
				if (dep.getSemanticRole() != SRLFrame.NONE) {
					final SyntaxTreeNode argumentConstituent = getArgumentConstituent(parse, dep);
					result.append(getPredicate(parse, dep.getPropbankPredicateIndex()));
					result.append(" " + dep.getSemanticRole());
					int i = 0;
					final List<SyntaxTreeNodeLeaf> argumentWords = argumentConstituent.getLeaves();
					for (final SyntaxTreeNodeLeaf child : argumentWords) {
						if (i == 0 && dep.getSemanticRole().isCoreArgument()
								&& child.getCategory().isFunctionInto(Category.PP) && argumentWords.size() > 1) {
							// Simplify arguments by dropping initial prepositions, as in PropBank.
						} else if (i == argumentWords.size() - 1 && child.getCategory().isPunctuation()) {
							// Drop trailing punctutation
						} else {
							result.append(" " + child.getWord());
							if (includeWordIndices) {
								result.append("@" + child.getHeadIndex());
							}
						}
						i++;
					}

					result.append("\n");
				}

			}

			if (deps.size() == 0) {
				System.out.println("No SRL relations found");
			}

		}

		private String getPredicate(final SyntaxTreeNode parse, final int index) {
			final SyntaxTreeNodeLeaf node = parse.getLeaves().get(index);
			final StringBuilder result = new StringBuilder();
			result.append(MorphaStemmer.stemToken(node.getWord()));
			final Category category = node.getCategory();
			final List<Integer> indices = new ArrayList<>();
			indices.add(index);

			// Add particles if necessary. e.g. give_up, take_off
			for (int i = 1; i <= category.getNumberOfArguments(); i++) {
				if (category.getArgument(i) == Category.PR) {
					for (final ResolvedDependency dep : parse.getAllLabelledDependencies()) {
						if (dep.getHead() == index && dep.getArgNumber() == i) {
							final String particle = parse.getLeaves().get(dep.getArgumentIndex()).getWord();
							indices.add(dep.getArgumentIndex());
							result.append("_");
							result.append(particle);
							break;
						}
					}
				}
			}

			if (includeWordIndices) {
				result.append("@");
				for (int i = 0; i < indices.size(); i++) {
					if (i > 0) {
						result.append(",");
					}
					result.append(indices.get(i));
				}
			}
			return result.toString();
		}

		private SyntaxTreeNode getArgumentConstituent(final SyntaxTreeNode node, final ResolvedDependency dep) {
			int excludeIndex;
			if (dep.getSemanticRole().isCoreArgument()
					&& Category.valueOf("S\\NP").matches(dep.getCategory().getArgument(dep.getArgNumber()))) {
				// Exclude predicates from VP argument constituents.
				// e.g. In "I like eating", the argument of 'like' shouldn't be 'I like eating' just because
				// 'eating' is the head.
				excludeIndex = dep.getPropbankPredicateIndex();
			} else {
				excludeIndex = -1;
			}

			return getArgumentConstituent(node, dep.getPropbankArgumentIndex(), excludeIndex);
		}

		private SyntaxTreeNode getArgumentConstituent(final SyntaxTreeNode node, final int head, final int excludeIndex) {
			final boolean exclude = excludeIndex >= node.getStartIndex() && excludeIndex < node.getEndIndex();

			if (!exclude && node.getDependencyStructure().getArbitraryHead() == head) {
				return node;
			}

			for (final SyntaxTreeNode child : node.getChildren()) {
				final SyntaxTreeNode result = getArgumentConstituent(child, head, excludeIndex);
				if (result != null) {
					return result;
				}
			}

			return null;
		}

		@Override
		protected boolean outputsLogic() {
			return false;
		}

		@Override
		protected boolean outputsDependencies() {
			return true;
		}
	}

	private static class HTMLPrinter extends ParsePrinter {
		@Override
		protected void printFailure(final StringBuilder result) {
			result.append("<p>Parse failed!</p>");
		}

		@Override
		protected void printHeader(final int id, final StringBuilder result) {

			result.append("<script>\n"
					+ "function drawArrowhead(ctx, locx, locy, angle, sizex, sizey) {\n"
					+ "    var hx = sizex / 2;\n"
					+ "    var hy = sizey / 2;\n"
					+ "    ctx.save();\n"
					+ "    ctx.translate((locx ), (locy));\n"
					+ "    ctx.rotate(angle);\n"
					+ "    ctx.translate(-hx,-hy);\n"
					+ "\n"
					+ "    ctx.beginPath();\n"
					+ "    ctx.moveTo(0,0);\n"
					+ "    ctx.lineTo(0,1*sizey);\n"
					+ "    ctx.lineTo(1*sizex,1*hy);\n"
					+ "    ctx.closePath();\n"
					+ "    ctx.fill();\n"
					+ "    ctx.restore();\n"
					+ "}\n"
					+ "\n"
					+ "\n"
					+ "\n"
					+ "\n"
					+ "function draw(context, startElement, endElement, label, id) {\n"
					+ "      var start  = document.getElementById('w' + startElement + '.' + id).getBoundingClientRect();;\n"
					+ "      var end  = document.getElementById('w' + endElement + '.' + id).getBoundingClientRect();;\n"
					+ "      var margin = document.getElementById('myCanvas"
					+ (parseID.get())
					+ "').getBoundingClientRect().left;\n"
					+ "      var sx = (start.left + start.right) / 2 - 0;\n"
					+ "      var ex = (end.left + end.right) / 2  - 0;\n"
					+ "      if (ex < sx) { ex = ex + 8; } else { ex = ex - 8; }"
					+ ""
					+ "\n"
					+ "      var x = ((sx + ex) / 2) - margin;\n"
					+ "\n"
					+ "      var theta = 0.2 * Math.PI;\n"
					+ "      var radius = Math.abs(ex - sx) / (2 * Math.sin(theta));\n"
					+ "      var y = canvas.height - 3 + radius * Math.cos(theta);\n"
					+ "\n"
					+ "      var theta2 = Math.PI / 2 - theta;\n"
					+ "      var startAngle = Math.PI + theta2;\n"
					+ "      var endAngle = -theta2;\n"
					+ "      var counterClockwise = false;\n"
					+ "\n"
					+ "\n"
					+ "      context.beginPath();\n"
					+ "      context.arc(x, y, radius, startAngle, endAngle, counterClockwise);\n"
					+ "      context.lineWidth = 2;\n"
					+ "\n"
					+ "      // line color\n"
					+ "      context.strokeStyle = 'black';\n"
					+ "      context.stroke();\n"
					+ "      context.closePath();\n"
					+ "      context.fillText(label, x - 10, y - radius - 3)\n"
					+ "      //alert(label);\n"
					+ "\n"
					+ "      var angle;\n"
					+ "      var angle2;\n"
					+ "      if (sx > ex) {\n"
					+ "        angle = startAngle;\n"
					+ "        angle2 = angle - Math.PI / 2;\n"
					+ "      } else {\n"
					+ "        angle = endAngle;\n"
					+ "        angle2 = angle + Math.PI / 2;\n"
					+ "      }\n"
					+ "\n"
					+ "      drawArrowhead(context, x + radius * Math.cos(angle), y + radius * Math.sin(angle), angle2, 10, 10);\n"
					+ "}\n" + "</script>\n");
		}

		@Override
		protected void printFooter(final StringBuilder result) {

			result.append("</body>\n" + "</html>");
		}

		private final static java.util.Set<String> shortChars = ImmutableSet
				.of(".", ",", "[", "]", "(", ")", "\\", "/");

		private int guessWidth(final String s) {
			final int longChar = 5;
			final int shortChar = 2;
			int result = 0;
			for (int i = 0; i < s.length(); i++) {
				if (shortChars.contains(s.charAt(i))) {
					result = result + shortChar;
				} else {
					result = result + longChar;
				}
			}

			return result;
		}

		private final AtomicInteger parseID = new AtomicInteger();

		@Override
		protected void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			final int numWords = parse.getLeaves().size();
			final int id = parseID.getAndIncrement();

			final int betweenWordSpace = 5;
			int parseWidth = 0;

			for (final SyntaxTreeNodeLeaf word : parse.getLeaves()) {
				parseWidth += Math.max(guessWidth(word.getWord().toString()), guessWidth(word.getCategory().toString())
						+ (word.getSemantics().isPresent() ? guessWidth(word.getSemantics().get().toString()) : 0));
				parseWidth += betweenWordSpace;
			}

			result.append("<table>");
			result.append("<tr><td colspan=" + (numWords + 1) + " style=\"padding-left:0px;padding-right:0px;\">\n"
					+ "<canvas id=\"myCanvas" + id + "\" width=" + (parseWidth)
					+ " height=200 display:block vertical-align:bottom></canvas></td></tr>");

			int wordIndex = 0;
			for (final List<SyntaxTreeNode> row : getRows(parse)) {
				result.append("<tr>");
				int indent = 0;
				while (indent < row.size()) {
					final SyntaxTreeNode cell = row.get(indent);

					if (cell == null) {
						result.append("<td></td>");
						indent = indent + 1;
					} else if (cell.isLeaf()) {
						result.append(makeCell(((SyntaxTreeNodeLeaf) cell).getWord(), cell.getCategory(),
								cell.getSemantics(), wordIndex, id));
						indent = indent + 1;
						wordIndex++;
					} else {
						final int width = getWidth(cell);
						result.append(makeCell(cell.getCategory(), cell.getSemantics(), width));
						indent = indent + width;
					}
				}
				result.append("<td width=" + "0" + " /></tr>\n");
			}
			result.append("</table>");

			result.append("<script>");
			result.append("      var canvas = document.getElementById('myCanvas" + id + "');\n");
			result.append("      var context = canvas.getContext('2d');\n");
			for (final ResolvedDependency dep : parse.getAllLabelledDependencies()) {
				if (showDependency(dep, parse)) {

					final String label;
					final int from;
					final int to;
					if (dep.getSemanticRole() != SRLFrame.NONE) {
						// Draw adjuncts PropBank-style, from the predicate to the modifier.
						label = dep.getSemanticRole().toString();
						from = dep.getPropbankPredicateIndex();
						to = dep.getPropbankArgumentIndex();
					} else {
						// Draw in some extra CCG dependencies for nouns and adjectives.
						label = "";
						from = dep.getHead();
						to = dep.getArgumentIndex();
					}

					result.append("draw(context, " + from + ", " + to + ", '" + label + "', " + id + ");");
				}

			}
			result.append("</script>");
		}

		/**
		 * Decide which dependencies are worth showing.
		 */
		private boolean showDependency(final ResolvedDependency dep, final SyntaxTreeNode parse) {
			final SyntaxTreeNodeLeaf predicateNode = parse.getLeaves().get(dep.getHead());
			if (dep.getHead() == dep.getArgumentIndex()) {
				return false;
			} else if (dep.getSemanticRole() != SRLFrame.NONE) {
				return true;
			} else if (predicateNode.getPos().startsWith("JJ") && dep.getCategory() != Category.valueOf("NP/N")) {
				// Adjectives, excluding determiners
				return true;
			} else if (Category.valueOf("(S\\NP)/(S\\NP)") == dep.getCategory()) {
				// Exclude auxiliary verbs. Hopefully the SRL will have already identified the times this category isn't
				// an auxiliary.
				return false;
			} else if (predicateNode.getPos().startsWith("NN") && dep.getCategory().isFunctionInto(Category.N)) {
				// Checking category to avoid annoying getting a "subject" dep on yesterday|NN|(S\NP)\(S\NP)
				return true;
			} else if (predicateNode.getPos().startsWith("VB")) {
				// Other verb arguments, e.g. particles
				return true;
			} else {
				return false;
			}
		}

		List<List<SyntaxTreeNode>> getRows(final SyntaxTreeNode parse) {
			final List<List<SyntaxTreeNode>> result = new ArrayList<>();
			getRows(parse, result, 0);
			return result;
		}

		int getWidth(final SyntaxTreeNode node) {
			if (node.getChildren().size() == 0) {
				return 1;
			} else {
				int result = 0;

				for (final SyntaxTreeNode child : node.getChildren()) {
					result = result + getWidth(child);
				}

				return result;
			}
		}

		@Override
		protected void printFileHeader(final StringBuilder result) {
		}

		int getRows(final SyntaxTreeNode node, final List<List<SyntaxTreeNode>> result, final int minIndentation) {

			if (node.getDependenciesLabelledAtThisNode().size() > 0) {
				return getRows(node.getChild(0), result, minIndentation);
			}
			int maxChildLevel = 0;
			int i = minIndentation;
			for (final SyntaxTreeNode child : node.getChildren()) {
				maxChildLevel = Math.max(getRows(child, result, i), maxChildLevel);
				i = i + getWidth(child);
			}

			int level;
			if (node.getChildren().size() > 0) {
				level = maxChildLevel + 1;
			} else {
				level = 0;
			}

			while (result.size() < level + 1) {
				result.add(new ArrayList<SyntaxTreeNode>());
			}
			while (result.get(level).size() < minIndentation + 1) {
				result.get(level).add(null);
			}

			result.get(level).set(minIndentation, node);
			return level;
		}

		private String makeCell(final Category category, final Optional<Logic> logic, final int width) {
			return "<td colspan=" + width + "><center><hr style=\"margin:5px\"/>" + getCategoryHTML(category) + "<br>"
					+ printLogic(logic) + "</center></td>";
		}

		private String makeCell(final String word, final Category category, final Optional<Logic> logic,
				final int index, final int parseNumber) {
			return "<td id=w" + index + "." + parseNumber + "><center><font face=\"Arial\">" + word
					+ "</font><hr style=\"margin:5px\" />" + getCategoryHTML(category) + "<br>" + printLogic(logic)
					+ "</center></td>";
		}

		private String getCategoryHTML(final Category c) {
			String asString = c.toString();
			asString = asString.replaceAll("\\[", "<sub>");
			asString = asString.replaceAll("\\]", "</sub>");
			return "<font face=\"gill sans\">" + asString + "</font>";
		}

		private String printLogic(final Optional<Logic> logic) {
			if (logic.isPresent()) {
				return "<font face=\"serif\"><i>" + Logic2Html.getHTML(logic.get()) + "</i></font>";
			} else {
				return "&nbsp;";
			}
		}

		@Override
		protected boolean outputsLogic() {
			return true;
		}

		@Override
		protected boolean outputsDependencies() {
			return true;
		}
	}

	private static class SupertagPrinter extends ParsePrinter {

		@Override
		protected void printFileHeader(final StringBuilder result) {
		}

		@Override
		protected void printFailure(final StringBuilder result) {
		}

		@Override
		protected void printHeader(final int id, final StringBuilder result) {
		}

		@Override
		protected void printFooter(final StringBuilder result) {
		}

		@Override
		protected void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			// word|pos|tag word|pos|tag word|pos|tag
			boolean isFirst = true;
			for (final SyntaxTreeNodeLeaf word : parse.getLeaves()) {
				if (isFirst) {
					isFirst = false;
				} else {
					result.append(" ");
				}

				result.append(word.getWord() + "|" + (word.getPos() == null ? "" : word.getPos()) + "|"
						+ word.getCategory());
			}
		}

		@Override
		protected boolean outputsLogic() {
			return false;
		}

		@Override
		protected boolean outputsDependencies() {
			return false;
		}
	}

	private static class LogicPrinter extends ParsePrinter {

		@Override
		protected void printFileHeader(final StringBuilder result) {
		}

		@Override
		protected void printFailure(final StringBuilder result) {
		}

		@Override
		protected void printHeader(final int id, final StringBuilder result) {
		}

		@Override
		protected void printFooter(final StringBuilder result) {
		}

		@Override
		protected void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			final List<List<ResolvedDependency>> labels = new ArrayList<>();
			for (final SyntaxTreeNodeLeaf leaf : parse.getLeaves()) {
				labels.add(new ArrayList<>(leaf.getCategory().getNumberOfArguments()));
				for (int i = 0; i < leaf.getCategory().getNumberOfArguments(); i++) {
					labels.get(labels.size() - 1).add(null); // TODO
				}
			}

			for (final ResolvedDependency dep : parse.getAllLabelledDependencies()) {

				final List<ResolvedDependency> labelsForWord = labels.get(dep.getHead());
				if (dep.getArgumentIndex() > -1) {
					labelsForWord.set(dep.getArgNumber() - 1, dep);
				}

			}

			Preconditions.checkState(parse.getSemantics().isPresent());
			result.append(parse.getSemantics().get());
			Util.debugHook();
		}

		@Override
		protected boolean outputsLogic() {
			return true;
		}

		@Override
		protected boolean outputsDependencies() {
			return true;
		}
	}

	private static class PrologPrinter extends ParsePrinter {

		/*
		 * ccg(2, ba('S[dcl]', lf(2,1,'NP'), fa('S[dcl]\NP', lf(2,2,'(S[dcl]\NP)/NP'), lex('N','NP', lf(2,3,'N'))))).
		 *
		 * w(2, 1, 'I', 'I', 'PRP', 'I-NP', 'O', 'NP'). w(2, 2, 'like', 'like', 'VBP', 'I-VP', 'O', '(S[dcl]\NP)/NP').
		 * w(2, 3, 'cake', 'cake', 'NN', 'I-NP', 'O', 'N').
		 */

		private static String getRuleName(final RuleType combinator) {
			// fa ba fc bx gfc gbx conj
			switch (combinator) {
			case FA:
				return "fa";
			case BA:
				return "ba";
			case FC:
				return "fc";
			case BX:
				return "bx";
			case CONJ:
				return "conj";
			case RP:
				return "rp";
			case LP:
				return "lp";
			case GFC:
				return "gfc";
			case GBX:
				return "gbx";
			default:
				throw new RuntimeException("Unknown rule type: " + combinator);
			}

		}

		@Override
		protected void printFileHeader(final StringBuilder result) {
			result.append(":- multifile w/8, ccg/2, id/2.\n" + ":- discontiguous w/8, ccg/2, id/2.\n"
					+ ":- dynamic w/8, ccg/2, id/2.\n" + "\n" + "");
		}

		@Override
		protected void printFailure(final StringBuilder result) {
		}

		@Override
		protected void printHeader(final int id, final StringBuilder result) {
		}

		@Override
		protected void printFooter(final StringBuilder result) {
		}

		@Override
		protected void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			printDerivation(parse, sentenceNumber, result);
			result.append("\n");

			int i = 0;
			for (final SyntaxTreeNodeLeaf word : parse.getLeaves()) {
				// w(2, 1, 'I', 'I', 'PRP', 'I-NP', 'I-PER', 'NP').
				i++;
				result.append("w(" + sentenceNumber + ", " + i + ", '" + word.getWord() + "', '"
						+ MorphaStemmer.stemToken(word.getWord(), word.getPos()) + "', '" + word.getPos() + "', 'O"
						+ "', '" + word.getNER() + "', '" + word.getCategory() + "').\n");
			}
		}

		private void printDerivation(final SyntaxTreeNode parse, final int id, final StringBuilder result) {
			result.append("ccg(" + id);
			parse.accept(new DerivationPrinter(result, id));
			result.append(").\n");
		}

		private class DerivationPrinter extends ParsePrinterVisitor {
			int currentIndent = 1;
			int wordNumber = 1;
			final int sentenceNumber;

			DerivationPrinter(final StringBuilder result, final int sentenceNumber) {
				super(result);
				this.sentenceNumber = sentenceNumber;
			}

			@Override
			public void visit(final SyntaxTreeNodeLabelling node) {
				node.getChild(0).accept(this);
			}

			@Override
			public void visit(final SyntaxTreeNodeBinary node) {
				// ba('S[dcl]',
				result.append(",\n");
				printIndent(currentIndent);
				result.append(getRuleName(node.getRuleType()) + "('" + node.getCategory() + "'");
				currentIndent++;
				node.getLeftChild().accept(this);
				node.getRightChild().accept(this);
				result.append(")");
				currentIndent--;
			}

			@Override
			public void visit(final SyntaxTreeNodeUnary node) {
				// lex('N','NP',
				result.append(",\n");
				printIndent(currentIndent);
				result.append(getUnaryRuleName(node.getChild().getCategory(), node.getCategory()) + "('"
						+ node.getChild().getCategory() + "','" + node.getCategory() + "'");
				currentIndent++;
				node.getChild().accept(this);
				result.append(")");
				currentIndent--;
			}

			@Override
			public void visit(final SyntaxTreeNodeLeaf node) {
				// lf(2,2,'(S[dcl]\NP)/NP'),
				result.append(",\n");
				printIndent(currentIndent);
				result.append("lf(" + sentenceNumber + "," + wordNumber + ",'" + node.getCategory() + "')");
				wordNumber++;
			}

			private void printIndent(final int currentIndent) {
				result.append(Strings.repeat(" ", currentIndent));
			}
		}

		@Override
		protected boolean outputsLogic() {
			return false;
		}

		@Override
		protected boolean outputsDependencies() {
			return false;
		}

	}

	private static String getUnaryRuleName(final Category initial, final Category result) {
		if ((Category.NP.matches(initial) || Category.PP.matches(initial)) && result.isTypeRaised()) {
			return "tr";
		} else {
			return "lex";
		}
	}

	private static class ExtendedCCGBankPrinter extends ParsePrinter {

		@Override
		protected void printFailure(final StringBuilder result) {
		}

		private class CCGBankParsePrinterVisitor extends ParsePrinterVisitor {
			// (<T S[wq] fa 0 2> (<L S[wq]/(S[dcl]\\NP) Who who WP O I-NP
			// S[wq]/(S[dcl]\\NP)>) (<T S[dcl]\\NP rp 0 2> (<T S[dcl]\\NP ba 0
			// 2> (<T S[dcl]\\NP fa 0 2> (<L (S[dcl]\\NP)/NP is be VBZ O I-VP
			// (S[dcl]\\NP)/NP>) (<T NP ba 0 2> (<T NP[nb] fa 1 2> (<L NP[nb]/N
			// the the DT O I-NP NP[nb]/N>) (<L N leader leader NN O I-NP N>))
			// (<T NP\\NP fa 0 2> (<L (NP\\NP)/NP of of IN O I-PP (NP\\NP)/NP>)
			// (<T NP lex 0 1> (<L N Libya Libya NNP I-LOC I-NP N>))))) (<T
			// (S\\NP)\\(S\\NP) fa 0 2> (<L ((S\\NP)\\(S\\NP))/NP in in IN O
			// I-PP ((S[X]\\NP)\\(S[X]\\NP))/NP>) (<T NP lex 0 1> (<L N 2011
			// 2011 CD I-DAT I-NP N>)))) (<L . ? ? . O O .>)))

			private CCGBankParsePrinterVisitor(final StringBuilder result) {
				super(result);
			}

			@Override
			public void visit(final SyntaxTreeNodeBinary node) {
				result.append("(<T ");
				result.append(node.getCategory().toString());
				result.append(" " + PrologPrinter.getRuleName(node.getRuleType()) + " "
						+ (node.isHeadIsLeft() ? "0" : "1") + " 2> ");
				node.getLeftChild().accept(this);
				node.getRightChild().accept(this);

				result.append(") ");

			}

			@Override
			public void visit(final SyntaxTreeNodeLabelling node) {
				node.getChild(0).accept(this);
			}

			@Override
			public void visit(final SyntaxTreeNodeUnary node) {
				result.append("(<T ");
				result.append(node.getCategory().toString());
				result.append(" ");
				result.append(getUnaryRuleName(node.getChild().getCategory(), node.getCategory()) + " 0 1> ");
				node.getChild().accept(this);

				result.append(") ");

			}

			@Override
			public void visit(final SyntaxTreeNodeLeaf node) {
				result.append("(<L ");
				result.append(node.getCategory().toString());
				result.append(" ");
				result.append(CCGBankPrinter.normalize(node.getWord()));
				result.append(" ");
				result.append(MorphaStemmer.stemToken(CCGBankPrinter.normalize(node.getWord()), node.getPos()));

				if (node.getPos() == null) {
					result.append(" NN ");
				} else {
					result.append(" " + node.getPos() + " ");
				}
				result.append(node.getNER());
				result.append(" O "); // ignoring chunking tags
				result.append(node.getCategory().toString());
				result.append(">) ");
			}
		}

		@Override
		protected void printHeader(final int id, final StringBuilder result) {
			result.append("ID=" + id + "\n");
		}

		@Override
		protected void printFooter(final StringBuilder result) {
		}

		@Override
		protected void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			parse.accept(new CCGBankParsePrinterVisitor(result));
		}

		@Override
		protected void printFileHeader(final StringBuilder result) {
		}

		@Override
		protected boolean outputsLogic() {
			return false;
		}

		@Override
		protected boolean outputsDependencies() {
			return false;
		}

	}

	public static class DependenciesPrinter extends ParsePrinter {

		@Override
		protected void printFileHeader(final StringBuilder result) {

		}

		@Override
		protected void printFailure(final StringBuilder result) {

		}

		@Override
		protected void printHeader(final int id, final StringBuilder result) {

		}

		@Override
		protected void printFooter(final StringBuilder result) {

		}

		@Override
		protected void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			final List<String> words = parse.getLeaves().stream().map(x -> x.getWord()).collect(Collectors.toList());
			for (final ResolvedDependency dep : parse.getAllLabelledDependencies()) {
				result.append(words.get(dep.getHead()));
				result.append("\t");
				result.append(dep.getHead());
				result.append("\t");
				result.append(dep.getSemanticRole());
				result.append("\t");
				result.append(words.get(dep.getArgumentIndex()));
				result.append("\t");
				result.append(dep.getArgumentIndex());
				result.append("\t");
				result.append(dep.getCategory());
				result.append("\t");
				result.append(dep.getArgNumber());
				result.append("\t");
				result.append(dep.getPreposition());
				result.append("\n");
			}
		}

		@Override
		protected boolean outputsLogic() {
			return false;
		}

		@Override
		protected boolean outputsDependencies() {
			return true;
		}

	}

	protected abstract boolean outputsLogic();

	public String printJointParses(final List<CCGandSRLparse> parses, final int id) {
		return print(parses == null ? null : parses.stream().map(x -> x.getCcgParse()).collect(Collectors.toList()), id);
	}

	/**
	 * Override this if only printing only syntactic output (which saves pipeline models from having to build
	 * dependencies).
	 */
	protected boolean outputsDependencies() {
		return true;
	}
}
