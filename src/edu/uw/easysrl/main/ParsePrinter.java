package edu.uw.easysrl.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Strings;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.lemmatizer.MorphaStemmer;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLabelling;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeVisitor;

public abstract class ParsePrinter {
	public final static ParsePrinter CCGBANK_PRINTER = new CCGBankPrinter();
	public final static ParsePrinter HTML_PRINTER = new HTMLPrinter();
	public final static ParsePrinter PROLOG_PRINTER = new PrologPrinter();
	public final static ParsePrinter EXTENDED_CCGBANK_PRINTER = new ExtendedCCGBankPrinter();
	public final static ParsePrinter SUPERTAG_PRINTER = new SupertagPrinter();
	public final static ParsePrinter SRL_PRINTER = new SRLprinter();

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

	abstract void printFileHeader(StringBuilder result);

	abstract void printFailure(StringBuilder result);

	abstract void printHeader(int id, StringBuilder result);

	abstract void printFooter(StringBuilder result);

	abstract void printParse(SyntaxTreeNode parse, int sentenceNumber, StringBuilder result);

	private abstract static class ParsePrinterVisitor implements SyntaxTreeNodeVisitor {
		final StringBuilder result;

		private ParsePrinterVisitor(final StringBuilder result) {
			this.result = result;
		}

	}

	private static class CCGBankPrinter extends ParsePrinter {

		@Override
		void printFailure(final StringBuilder result) {
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
		void printHeader(final int id, final StringBuilder result) {
			result.append("ID=" + id + "\n");
		}

		@Override
		void printFooter(final StringBuilder result) {
		}

		@Override
		void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			parse.accept(new CCGBankParsePrinterVisitor(result));
		}

		@Override
		void printFileHeader(final StringBuilder result) {
		}
	}

	private static class SRLprinter extends ParsePrinter {

		@Override
		void printFileHeader(final StringBuilder result) {
		}

		@Override
		void printFailure(final StringBuilder result) {
		}

		@Override
		void printHeader(final int id, final StringBuilder result) {
		}

		@Override
		void printFooter(final StringBuilder result) {
		}

		@Override
		void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			for (final ResolvedDependency dep : parse.getAllLabelledDependencies()) {
				// output = output + "\n" + dep.toString();
				if (dep.getSemanticRole() != SRLFrame.NONE) {
					result.append(getPredicate(parse, dep.getPropbankPredicateIndex()));
					result.append(" " + dep.getSemanticRole());
					final SyntaxTreeNode argumentConstituent = getArgumentConstituent(parse,
							dep.getPropbankArgumentIndex());
					int i = 0;
					final List<SyntaxTreeNodeLeaf> argumentWords = argumentConstituent.getWords();
					for (final SyntaxTreeNodeLeaf child : argumentWords) {
						if (i == 0 && dep.getSemanticRole().isCoreArgument()
								&& child.getCategory().isFunctionInto(Category.PP) && argumentWords.size() > 1) {
							// Simplify arguments by dropping initial prepositions, as in PropBank.
						} else if (i == argumentWords.size() - 1 && child.getCategory().isPunctuation()) {
							// Drop trailing punctutation
						} else {
							result.append(" " + child.getWord());
						}
						i++;
					}
					result.append("\n");
				}

			}

		}

		private String getPredicate(final SyntaxTreeNode parse, final int index) {
			final SyntaxTreeNodeLeaf node = parse.getWords().get(index);
			final String result = node.getWord();
			final Category category = node.getCategory();

			// Add particles if necessary. e.g. give_up, take_off
			for (int i = 1; i <= category.getNumberOfArguments(); i++) {
				if (category.getArgument(i) == Category.PR) {
					for (final ResolvedDependency dep : parse.getAllLabelledDependencies()) {
						if (dep.getPredicateIndex() == index && dep.getArgNumber() == i) {
							final String particle = parse.getWords().get(dep.getArgumentIndex()).getWord();
							return result + "_" + particle;
						}
					}
				}
			}

			return result;
		}

		private SyntaxTreeNode getArgumentConstituent(final SyntaxTreeNode node, final int head) {
			if (node.getDependencyStructure().getArbitraryHead() == head) {
				return node;
			}

			for (final SyntaxTreeNode child : node.getChildren()) {
				final SyntaxTreeNode result = getArgumentConstituent(child, head);
				if (result != null) {
					return result;
				}
			}

			return null;
		}
	}

	private static class HTMLPrinter extends ParsePrinter {
		@Override
		void printFailure(final StringBuilder result) {
			result.append("<p>Parse failed!</p>");
		}

		@Override
		void printHeader(final int id, final StringBuilder result) {

			result.append("<html>\n" + "<body>\n");
		}

		@Override
		void printFooter(final StringBuilder result) {

			result.append("</body>\n" + "</html>");
		}

		@Override
		void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			result.append("<table>");
			for (final List<SyntaxTreeNode> row : getRows(parse)) {
				result.append("<tr>");
				int indent = 0;
				while (indent < row.size()) {
					final SyntaxTreeNode cell = row.get(indent);

					if (cell == null) {
						result.append("<td></td>");
						indent = indent + 1;
					} else if (cell.isLeaf()) {
						result.append(makeCell(((SyntaxTreeNodeLeaf) cell).getWord(), cell.getCategory()));
						indent = indent + 1;
					} else {
						final int width = getWidth(cell);
						result.append(makeCell(cell.getCategory(), width));
						indent = indent + width;
					}
				}
				result.append("</tr>\n");
			}
			result.append("</table>");
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
		void printFileHeader(final StringBuilder result) {
		}

		int getRows(final SyntaxTreeNode node, final List<List<SyntaxTreeNode>> result, final int minIndentation) {

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

		private String makeCell(final Category category, final int width) {
			return "<td colspan=" + width + "><center><hr>" + category.toString() + "</center></td>";
		}

		private String makeCell(final String word, final Category category) {
			return "<td><center>" + word + "<hr>" + category + "</center></td>";
		}
	}

	private static class SupertagPrinter extends ParsePrinter {

		@Override
		void printFileHeader(final StringBuilder result) {
		}

		@Override
		void printFailure(final StringBuilder result) {
		}

		@Override
		void printHeader(final int id, final StringBuilder result) {
		}

		@Override
		void printFooter(final StringBuilder result) {
		}

		@Override
		void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			// word|pos|tag word|pos|tag word|pos|tag
			boolean isFirst = true;
			for (final SyntaxTreeNodeLeaf word : parse.getWords()) {
				if (isFirst) {
					isFirst = false;
				} else {
					result.append(" ");
				}

				result.append(word.getWord() + "|" + (word.getPos() == null ? "" : word.getPos()) + "|"
						+ word.getCategory());
			}
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
		void printFileHeader(final StringBuilder result) {
			result.append(":- multifile w/8, ccg/2, id/2.\n" + ":- discontiguous w/8, ccg/2, id/2.\n"
					+ ":- dynamic w/8, ccg/2, id/2.\n" + "\n" + "");
		}

		@Override
		void printFailure(final StringBuilder result) {
		}

		@Override
		void printHeader(final int id, final StringBuilder result) {
		}

		@Override
		void printFooter(final StringBuilder result) {
		}

		@Override
		void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			printDerivation(parse, sentenceNumber, result);
			result.append("\n");

			int i = 0;
			for (final SyntaxTreeNodeLeaf word : parse.getWords()) {
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
		void printFailure(final StringBuilder result) {
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
		void printHeader(final int id, final StringBuilder result) {
			result.append("ID=" + id + "\n");
		}

		@Override
		void printFooter(final StringBuilder result) {
		}

		@Override
		void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			parse.accept(new CCGBankParsePrinterVisitor(result));
		}

		@Override
		void printFileHeader(final StringBuilder result) {
		}

	}

	public static class DependenciesPrinter extends ParsePrinter {

		@Override
		void printFileHeader(final StringBuilder result) {
			result.append("Empty header\n");
			result.append("To keep C&C evaluate script happy\n");
			result.append("\n");
		}

		@Override
		void printFailure(final StringBuilder result) {
		}

		@Override
		void printHeader(final int id, final StringBuilder result) {
		}

		@Override
		void printFooter(final StringBuilder result) {
		}

		@Override
		void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
			/*
			 * Pierre_1 (N{Y}/N{Y}<1>){_} 1 Vinken_2 0 61_4 (N{Y}/N{Y}<1>){_} 1 years_5 0 old_6
			 * ((S[adj]{_}\NP{Y}<1>){_}\NP{Z}<2>){_} 2 years_5 0 old_6 ((S[adj]{_}\NP{Y}<1>){_}\NP{Z}<2>){_} 1 Vinken_2
			 * 6 the_10 (NP[nb]{Y}/N{Y}<1>){_} 1 board_11 0 join_9 ((S[b]{_}\NP{Y}<1>){_}/NP{Z}<2>){_} 2 board_11 0
			 * nonexecutive_14 (N{Y}/N{Y}<1>){_} 1 director_15 0 a_13 (NP[nb]{Y}/N{Y}<1>){_} 1 director_15 0 as_12
			 * (((S[X]{Y}\NP{Z}){Y}\(S[X]{Y}<1>\NP{Z}){Y}){_}/NP{W}<2>){_} 3 director_15 0 as_12
			 * (((S[X]{Y}\NP{Z}){Y}\(S[X]{Y}<1>\NP{Z}){Y}){_}/NP{W}<2>){_} 2 join_9 0 Nov._16
			 * (((S[X]{Y}\NP{Z}){Y}\(S[X]{Y}<1>\NP{Z}){Y}){_}/N[num]{W}<2>){_} 3 29_17 0 Nov._16
			 * (((S[X]{Y}\NP{Z}){Y}\(S[X]{Y}<1>\NP{Z}){Y}){_}/N[num]{W}<2>){_} 2 join_9 0 will_8
			 * ((S[dcl]{_}\NP{Y}<1>){_}/(S[b]{Z}<2>\NP{Y*}){Z}){_} 2 join_9 0 will_8
			 * ((S[dcl]{_}\NP{Y}<1>){_}/(S[b]{Z}<2>\NP{Y*}){Z}){_} 1 Vinken_2 0 join_9
			 * ((S[b]{_}\NP{Y}<1>){_}/NP{Z}<2>){_} 1 Vinken_2 0 ((S[dcl]{X}\NP{Y}<15>){X}/(S[b]{Z}<16>\NP{Y*}){Z}){X}
			 * <c> Pierre|NNP|N/N Vinken|NNP|N ,|,|, 61|CD|N/N years|NNS|N old|JJ|(S[adj]\NP)\NP ,|,|,
			 * will|MD|(S[dcl]\NP)/(S[b]\NP) join|VB|(S[b]\NP)/NP the|DT|NP[nb]/N board|NN|N as|IN|((S\NP)\(S\NP))/NP
			 * a|DT|NP[nb]/N nonexecutive|JJ|N/N director|NN|N Nov.|NNP|((S\NP)\(S\NP))/N[num] 29|CD|N[num] .|.|.
			 */
			String depParse;

			depParse = CCGBankDependencies.getDependenciesAsString(Arrays.asList(parse), sentenceNumber);
			for (final String line : depParse.split("\n")) {
				if (!line.startsWith("#") && !line.trim().isEmpty()) {
					result.append(line);
					result.append("\n");
				}
			}
			result.append("<c>");
			for (final SyntaxTreeNodeLeaf word : parse.getWords()) {
				result.append(" " + word.getWord() + "|" + (word.getPos() == null ? "" : word.getPos()) + "|"
						+ word.getCategory());
			}
			result.append("\n");
		}

	}
}
