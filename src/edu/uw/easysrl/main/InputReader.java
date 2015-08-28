package edu.uw.easysrl.main;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import edu.uw.easysrl.main.EasySRL.InputFormat;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.util.Util;

public abstract class InputReader {

	public static class InputWord implements Serializable {
		private static final long serialVersionUID = -4110997736066926795L;

		public InputWord(final String word, final String pos, final String ner) {
			this.word = word;
			this.pos = pos;
			this.ner = ner;
			this.lowerCase = word.toLowerCase();
		}

		InputWord(final String word) {
			this(word, null, null);
		}

		public final String word;
		public final String pos;
		public final String ner;
		public final String lowerCase;

		public static List<InputWord> listOf(final String... words) {
			return listOf(Arrays.asList(words));
		}

		public static List<InputWord> listOf(final List<String> words) {
			final List<InputWord> result = new ArrayList<>(words.size());
			for (final String word : words) {
				result.add(new InputWord(word));
			}
			return result;
		}

		public static List<InputWord> fromLeaves(
				final List<SyntaxTreeNodeLeaf> leaves) {
			final List<InputWord> result = new ArrayList<>(leaves.size());
			for (final SyntaxTreeNodeLeaf leaf : leaves) {
				result.add(InputWord.valueOf(leaf));
			}

			return result;
		}

		private static InputWord valueOf(final SyntaxTreeNodeLeaf leaf) {
			return new InputWord(leaf.getWord(), leaf.getPos(), leaf.getNER());
		}

		@Override
		public int hashCode() {
			return Objects.hash(word, ner, pos);
		}

		@Override
		public boolean equals(final Object obj) {
			final InputWord other = (InputWord) obj;
			return Objects.equals(word, other.word)
					&& Objects.equals(ner, other.ner)
					&& Objects.equals(pos, other.pos);
		}

		@Override
		public String toString() {
			return word + (pos != null ? "|" + pos : "")
					+ (ner != null ? "|" + ner : "");
		}
	}

	Iterable<InputToParser> readFile(final File input) throws IOException {
		final Iterator<String> inputIt = Util.readFileLineByLine(input);

		return new Iterable<InputToParser>() {

			@Override
			public Iterator<InputToParser> iterator() {
				return new Iterator<InputToParser>() {

					private InputToParser next = getNext();

					@Override
					public boolean hasNext() {
						return next != null;
					}

					private InputToParser getNext() {
						while (inputIt.hasNext()) {
							final String nextLine = inputIt.next();
							if (!nextLine.startsWith("#")
									&& !nextLine.isEmpty()) {
								// Skip commented or empty lines;
								return readInput(nextLine);
							}
						}

						return null;
					}

					@Override
					public InputToParser next() {
						final InputToParser result = next;
						next = getNext();
						return result;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}

				};
			}

		};
	}

	public abstract InputToParser readInput(String line);

	public static class InputToParser {
		private final List<InputWord> words;
		private final boolean isAlreadyTagged;

		public InputToParser(final List<InputWord> words,
				final List<Category> goldCategories,
				final List<List<SyntaxTreeNodeLeaf>> inputSupertags,
				final boolean isAlreadyTagged) {
			this.words = words;
			this.goldCategories = goldCategories;
			this.inputSupertags = inputSupertags;
			this.isAlreadyTagged = isAlreadyTagged;
		}

		private final List<Category> goldCategories;
		private final List<List<SyntaxTreeNodeLeaf>> inputSupertags;

		public int length() {
			return words.size();
		}

		/**
		 * If true, the Parser should not supertag the data itself, and use
		 * getInputSupertags() instead.
		 */
		public boolean isAlreadyTagged() {
			return isAlreadyTagged;
		}

		public List<List<SyntaxTreeNodeLeaf>> getInputSupertags() {
			return inputSupertags;
		}

		public List<SyntaxTreeNodeLeaf> getInputSupertags1best() {
			final List<SyntaxTreeNodeLeaf> result = new ArrayList<>();
			for (final List<SyntaxTreeNodeLeaf> tagsForWord : inputSupertags) {
				result.add(tagsForWord.get(0));
			}
			return result;
		}

		public boolean haveGoldCategories() {
			return getGoldCategories() != null;
		}

		public List<Category> getGoldCategories() {
			return goldCategories;
		}

		public List<InputWord> getInputWords() {
			return words;

		}

		public String getWordsAsString() {
			final StringBuilder result = new StringBuilder();
			for (final InputWord word : words) {
				result.append(word.word + " ");
			}

			return result.toString().trim();
		}

		public static InputToParser fromTokens(final List<String> tokens) {
			final List<InputWord> inputWords = new ArrayList<>(tokens.size());
			for (final String word : tokens) {
				inputWords.add(new InputWord(word, null, null));
			}
			return new InputToParser(inputWords, null, null, false);
		}

	}

	private static class RawInputReader extends InputReader {

		@Override
		public InputToParser readInput(final String line) {
			// TODO quotes
			return InputToParser.fromTokens(Arrays.asList(line
					.replaceAll("\"", "").replaceAll("  +", " ").trim()
					.split(" ")));
		}
	}

	/**
	 * Reads input tagged with a distribution of supertags. The format can be
	 * produced running the C&C supertagger with the output format:
	 * %w\t%p\t%S|\n
	 *
	 * Example: Pierre NNP 0 N/N 0.99525070603732 N 0.0026450007306822|Vinken
	 * NNP 0 N 0.70743834018551 S/S...
	 */

	private static class GoldInputReader extends InputReader {

		@Override
		public InputToParser readInput(final String line) {
			final List<Category> result = new ArrayList<>();
			final String[] goldEntries = line.split(" ");
			final List<InputWord> words = new ArrayList<>(goldEntries.length);
			final List<List<SyntaxTreeNodeLeaf>> supertags = new ArrayList<>();
			for (final String entry : goldEntries) {
				final String[] goldFields = entry.split("\\|");

				if (goldFields[0].equals("\"")) {
					continue; // TODO quotes
				}
				if (goldFields.length < 3) {
					throw new InputMismatchException(
							"Invalid input: expected \"word|POS|SUPERTAG\" but was: "
									+ entry);
				}

				final String word = goldFields[0];
				final String pos = goldFields[1];
				final Category category = Category.valueOf(goldFields[2]);
				words.add(new InputWord(word));
				result.add(category);
				supertags.add(Arrays.asList(new SyntaxTreeNodeLeaf(word, pos,
						null, category, supertags.size())));
			}
			return new InputToParser(words, result, supertags, false);
		}

		private GoldInputReader() {
		}
	}

	private static class POSTaggedInputReader extends InputReader {

		@Override
		public InputToParser readInput(final String line) {
			final String[] taggedEntries = line.split(" ");
			final List<InputWord> inputWords = new ArrayList<>(
					taggedEntries.length);
			for (final String entry : taggedEntries) {
				final String[] taggedFields = entry.split("\\|");

				if (taggedFields.length < 2) {
					throw new InputMismatchException(
							"Invalid input: expected \"word|POS\" but was: "
									+ entry);
				}
				if (taggedFields[0].equals("\"")) {
					continue; // TODO quotes
				}
				inputWords.add(new InputWord(taggedFields[0], taggedFields[1],
						null));
			}
			return new InputToParser(inputWords, null, null, false);
		}
	}

	private static class POSandNERTaggedInputReader extends InputReader {
		@Override
		public InputToParser readInput(final String line) {
			final String[] taggedEntries = line.split(" ");
			final List<InputWord> inputWords = new ArrayList<>(
					taggedEntries.length);
			for (final String entry : taggedEntries) {
				final String[] taggedFields = entry.split("\\|");

				if (taggedFields[0].equals("\"")) {
					continue; // TODO quotes
				}
				if (taggedFields.length < 3) {
					throw new InputMismatchException(
							"Invalid input: expected \"word|POS|NER\" but was: "
									+ entry
									+ "\n"
									+ "The C&C can produce this format using: \"bin/pos -model models/pos | bin/ner -model models/ner -ofmt \"%w|%p|%n \\n\"\"");
				}
				inputWords.add(new InputWord(taggedFields[0], taggedFields[1],
						taggedFields[2]));
			}
			return new InputToParser(inputWords, null, null, false);
		}
	}

	public static InputReader make(final InputFormat inputFormat) {
		switch (inputFormat) {
		case TOKENIZED:
			return new RawInputReader();
		case GOLD:
			return new GoldInputReader();
		case POSTAGGED:
			return new POSTaggedInputReader();
		case POSANDNERTAGGED:
			return new POSandNERTaggedInputReader();
		default:
			throw new Error("Unknown input format: " + inputFormat);
		}
	}
}
