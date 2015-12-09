package edu.uw.easysrl.corpora;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.uw.easysrl.util.Util;

public class PennTreebank {

	Table<String, Integer, TreebankParse> readCorpus(final File folder)
			throws IOException {
		final Table<String, Integer, TreebankParse> result = HashBasedTable
				.create();
		for (final File file : Util.findAllFiles(folder, ".*.(mrg|MRG)")) {
			final String name = file.getName().substring(0,
					file.getName().length() - 4).toLowerCase();
			// System.out.println(name);
			final Iterator<String> lines = Util.readFileLineByLine(file);
			int sentenceNumber = 0;
			while (lines.hasNext()) {
				result.put(name, sentenceNumber, readParse(lines));
				sentenceNumber++;
			}
		}

		return result;
	}

	/*
	 * ( (S (NP-SBJ (NP (NNP Pierre) (NNP Vinken) ) (, ,) (ADJP (NP (CD 61) (NNS
	 * years) ) (JJ old) ) (, ,) ) (VP (MD will) (VP (VB join) (NP (DT the) (NN
	 * board) ) (PP-CLR (IN as) (NP (DT a) (JJ nonexecutive) (NN director) ))
	 * (NP-TMP (NNP Nov.) (CD 29) ))) (. .) ))
	 */
	private TreebankParse readParse(final Iterator<String> lines) {
		final Stack<TreebankNode> stack = new Stack<>();
		final List<TreebankTerminal> leafs = new ArrayList<>();
		int wordIndexExcludingTraces = 0;
		int wordIndexIncludingTraces = 0;
		TreebankNode root = null;

		while (wordIndexExcludingTraces == 0 || stack.size() > 0) {
			String line = lines.next().trim();

			if (wordIndexExcludingTraces == 0 && !line.isEmpty()
					&& !line.startsWith("(")) {
				throw new RuntimeException("Parse error");
			}

			int i = 0;
			while (i < line.length()) {
				line = line.replaceAll("\\(\\(", "( ("); // How lazy am I?

				if (line.charAt(i) == '(') {
					int j = line.indexOf(' ', i);
					if (j == -1) {
						j = line.length();
					}
					final String label = line.substring(i + 1, j);
					i = j + 1;

					if (i >= line.length() || line.charAt(i) == '(') {
						// Non-terminal
						stack.push(new TreebankNonTerminal(
								stack.size() == 0 ? null : stack.peek(), label));
					} else {
						// Terminal
						final String word = line.substring(i,
								line.indexOf(")", i + 1));

						final TreebankTerminal leaf = new TreebankTerminal(
								stack.peek(), label, wordIndexExcludingTraces,
								wordIndexIncludingTraces, word);
						leafs.add(leaf);

						if (!leaf.isTrace()) {
							wordIndexExcludingTraces = wordIndexExcludingTraces + 1;

						}
						wordIndexIncludingTraces++;

						i = i + word.length() + 1;
					}

					if (stack.size() == 1) {
						root = stack.peek();
					}

				} else if (line.charAt(i) == ')') {
					i++;
					stack.pop();
				} else if (line.charAt(i) == ' ') {
					i++;
					continue;
				}
			}
		}

		return new TreebankParse(root.children.get(0), leafs);
	}

	public class TreebankParse {
		private final List<TreebankTerminal> words;
		private final TreebankNode root;

		private TreebankParse(final TreebankNode root,
				final List<TreebankTerminal> words) {
			super();
			this.words = words;
			this.root = root;
		}

		TreebankNode getWord(final int startIndexIncludingTraces) {
			return words.get(startIndexIncludingTraces);
		}

		public List<String> getWords() {
			final List<String> result = new ArrayList<>();
			for (final TreebankTerminal terminal : words) {
				if (!terminal.isTrace()) {
					result.add(terminal.word);
				}
			}

			return result;
		}

		public String getRootLabel() {
			return root.label;
		}
	}

	private class TreebankNonTerminal extends TreebankNode {

		private TreebankNonTerminal(final TreebankNode parent,
				final String label) {
			super(parent, label);
		}

		@Override
		int getStartIndex(final boolean includeTraces) {
			return children.get(0).getStartIndex(includeTraces);
		}

		@Override
		int getEndIndex(final boolean includeTraces) {
			return children.get(children.size() - 1).getEndIndex(includeTraces);

		}

	}

	private class TreebankTerminal extends TreebankNode {
		private final int wordIndexExcludingTraces;
		private final int wordIndexIncludingTraces;
		private final String word;

		private TreebankTerminal(final TreebankNode parent, final String label,
				final int wordIndexExcludingTraces,
				final int wordIndexIncludingTraces, final String word) {
			super(parent, label);
			this.wordIndexIncludingTraces = wordIndexIncludingTraces;
			this.wordIndexExcludingTraces = wordIndexExcludingTraces;
			this.word = word;

		}

		@Override
		int getStartIndex(final boolean includeTraces) {
			return includeTraces ? wordIndexIncludingTraces
					: wordIndexExcludingTraces;
		}

		@Override
		int getEndIndex(final boolean includeTraces) {
			return getStartIndex(includeTraces)
					+ (!includeTraces && isTrace() ? 0 : 1);
		}
	}

	abstract class TreebankNode {

		private final TreebankNode parent;
		private final String label;
		final List<TreebankNode> children = new ArrayList<>();

		private TreebankNode(final TreebankNode parent, final String label) {
			super();
			this.parent = parent;
			if (parent != null) {
				parent.addChild(this);
			}
			this.label = label;
		}

		TreebankNode getParent() {
			return parent;
		}

		private void addChild(final TreebankNode node) {
			children.add(node);

		}

		public String getLabel() {
			return label;
		}

		abstract int getStartIndex(boolean includeTraces);

		abstract int getEndIndex(boolean includeTraces);

		boolean isTrace() {
			return label.equals("-NONE-");
		}

	}

}
