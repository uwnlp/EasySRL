package edu.uw.easysrl.corpora;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.util.Util;

/**
 * Reads in gold-standard parses from CCGBank.
 */
public class CCGBankParseReader {

	private final static String OPEN_BRACKET = "(<";
	private final static String OPEN_LEAF = "(<L ";
	private final static String SPLIT_REGEX = " |>\\)";

	public static SyntaxTreeNode parse(final String input) {
		return parse(input, new AtomicInteger(0));
	}

	public final static String devRegex = "wsj_00.*";
	public final static String trainRegex = "wsj_((0[2-9])|(1[0-9])|(2[0-1])).*";
	public final static String testRegex = "wsj_23.*";
	public final static String tuneRegex = "wsj_01.*";

	public static List<SyntaxTreeNode> loadCorpus(final File folder, final boolean isDev) throws IOException {
		final String regex = isDev ? devRegex : trainRegex;

		return Lists.newArrayList(loadCorpus(folder, regex + ".*.auto"));
	}

	public static Iterator<SyntaxTreeNode> loadCorpus(final File folder, final String regex) throws IOException {
		final List<File> autoFilesList = Util.findAllFiles(folder, regex);
		Collections.sort(autoFilesList);

		return new Iterator<SyntaxTreeNode>() {
			Iterator<File> autoFiles = ImmutableList.copyOf(autoFilesList).iterator();
			Iterator<String> autoLines = Util.readFileLineByLine(autoFiles.next());

			@Override
			public boolean hasNext() {
				return autoLines.hasNext() || autoFiles.hasNext();
			}

			@Override
			public SyntaxTreeNode next() {

				// Breaks if the last parse is a failure...
				if (autoLines.hasNext()) {
					String line = autoLines.next();
					while (autoLines.hasNext() && line.startsWith("ID=")) {
						line = autoLines.next();
					}

					if (!line.startsWith("ID=")) {
						final SyntaxTreeNode result = parse(line);
						return result;
					} else {
						return next();
					}

				} else {

					final File autoFile = autoFiles.next();
					try {
						autoLines = Util.readFileLineByLine(autoFile);
					} catch (final IOException e) {
						throw new RuntimeException(e);
					}

					return next();
				}
			}
		};

	}

	private static SyntaxTreeNode parse(final String input, final AtomicInteger wordIndex) {
		final int closeBracket = Util.findClosingBracket(input, 0);
		final int nextOpenBracket = input.indexOf(OPEN_BRACKET, 1);

		SyntaxTreeNode result;

		if (input.startsWith(OPEN_LEAF)) {
			// LEAF NODE
			final String[] parse = input.split(SPLIT_REGEX);

			if (parse.length < 6) {
				return null;
			}

			String catString = parse[1];

			if (catString.indexOf("@") > -1) {
				catString = catString.substring(0, catString.indexOf("@"));
			}

			result = new SyntaxTreeNodeLeaf(new String(parse[4]), new String(parse[2]), null,
					Category.valueOf(catString), wordIndex.getAndIncrement());
		} else {
			final int subtermCloseBracket = Util.findClosingBracket(input, nextOpenBracket);

			final SyntaxTreeNode child1 = parse(
					input.substring(nextOpenBracket, Util.findClosingBracket(input, nextOpenBracket) + 1), wordIndex);

			final int catEndIndex = input.indexOf(' ', 4);

			final String catString = input.substring(4, catEndIndex);

			Category cat;
			if (catString.endsWith("[conj]")) {
				final Category c = Category.valueOf(catString.substring(0, catString.lastIndexOf("[conj]")));
				cat = Category.make(c, Slash.BWD, c);
			} else {
				cat = Category.valueOf(catString);
			}

			final int headIndex = Integer.parseInt(input.substring(catEndIndex + 1, catEndIndex + 2));

			if (subtermCloseBracket == closeBracket - 2 || subtermCloseBracket == closeBracket - 1) {
				// Unary
				result = new SyntaxTreeNodeUnary(cat, child1, null, null, Collections.emptyList());

			} else {
				// Binary
				final String childString = input.substring(subtermCloseBracket + 2,
						Util.findClosingBracket(input, subtermCloseBracket + 2) + 1);
				final SyntaxTreeNode child2 = parse(childString, wordIndex);

				if (child2 == null) {
					// Bad brackets
					throw new IllegalArgumentException("Badly bracketed string: " + child2);
				}

				Combinator combinator = null;
				for (final Combinator c : Combinator.STANDARD_COMBINATORS) {
					if (c.canApply(child1.getCategory(), child2.getCategory())
							&& c.apply(child1.getCategory(), child2.getCategory()).equals(cat)) {
						combinator = c;
						break;
					}
				}

				result = new SyntaxTreeNodeBinary(cat, child1, child2, combinator != null ? combinator.getRuleType()
						: RuleType.NOISE, headIndex == 0, null, Collections.emptyList());
			}
		}

		return result;
	}

}
