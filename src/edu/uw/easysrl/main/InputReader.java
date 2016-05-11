package edu.uw.easysrl.main;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import edu.uw.Taggerflow;
import edu.uw.TaggerflowProtos.TaggedSentence;
import edu.uw.TaggerflowProtos.TaggedToken;
import edu.uw.easysrl.main.EasySRL.InputFormat;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.tagger.Tagger.ScoredCategory;
import edu.uw.easysrl.syntax.tagger.TaggerflowLSTM;
import edu.uw.easysrl.util.Util;

public abstract class InputReader {

	public static class InputWord implements Serializable {
		private static final long serialVersionUID = -4110997736066926795L;

		public InputWord(final String word, final String pos, final String ner) {
			this.word = word;
			this.pos = pos;
			this.ner = ner;
		}

		InputWord(final String word) {
			this(word, null, null);
		}

		public final String word;
		public final String pos;
		public final String ner;

		public static List<InputWord> listOf(final String... words) {
			final List<InputWord> result = new ArrayList<>(words.length);
			for (int i = 0; i < words.length; i++) {
				result.add(new InputWord(words[i]));
			}
			return result;
		}

		public static List<InputWord> listOf(final List<String> words) {
			final List<InputWord> result = new ArrayList<>(words.size());
			for (final String word : words) {
				result.add(new InputWord(word));
			}
			return result;
		}

		public static List<InputWord> fromLeaves(final List<SyntaxTreeNodeLeaf> leaves) {
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
			return Objects.equals(word, other.word) && Objects.equals(ner, other.ner) && Objects.equals(pos, other.pos);
		}

		@Override
		public String toString() {
			return word + (pos != null ? "|" + pos : "") + (ner != null ? "|" + ner : "");
		}
	}

	public Iterable<InputToParser> readFile(final File input) throws IOException {
		final Iterator<String> inputIt = Util.readFileLineByLine(input);

		return () -> new Iterator<InputToParser>() {
			private InputToParser next = getNext();

			@Override
			public boolean hasNext() {
				return next != null;
			}

			private InputToParser getNext() {
				while (inputIt.hasNext()) {
					final String nextLine = inputIt.next();
					if (!nextLine.startsWith("#") && !nextLine.isEmpty()) {
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

	public Iterator<InputToParser> readInput(final File file) throws IOException {
		final Iterator<String> lines = Util.readFileLineByLine(file);
		return new Iterator<InputToParser>() {

			@Override
			public boolean hasNext() {
				return lines.hasNext();
			}

			@Override
			public InputToParser next() {
				return readInput(lines.next());
			}
		};
	}

	public abstract InputToParser readInput(String line);

	public static class InputToParser implements Serializable {
		private static final long serialVersionUID = 1L;

		private final List<InputWord> words;
		private final boolean isAlreadyTagged;

		public InputToParser(final List<InputWord> words, final List<Category> goldCategories,
				final List<List<ScoredCategory>> inputSupertags, final boolean isAlreadyTagged) {
			this.words = words;
			this.goldCategories = goldCategories;
			this.inputSupertags = inputSupertags;
			this.isAlreadyTagged = isAlreadyTagged;
		}

		private final List<Category> goldCategories;
		private final List<List<ScoredCategory>> inputSupertags;

		public int length() {
			return words.size();
		}

		/**
		 * If true, the Parser should not supertag the data itself, and use getInputSupertags() instead.
		 */
		public boolean isAlreadyTagged() {
			return isAlreadyTagged;
		}

		public List<List<ScoredCategory>> getInputSupertags() {
			return inputSupertags;
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

		public boolean isPOStagged() {
			return words.size() == 0 || words.get(0).pos != null;
		}
	}

	private static class RawInputReader extends InputReader {

		@Override
		public InputToParser readInput(final String line) {
			// TODO quotes
			return InputToParser
					.fromTokens(Arrays.asList(line.replaceAll("\"", "").replaceAll("  +", " ").trim().split(" ")));
		}
	}

	/**
	 * Reads input tagged with a distribution of supertags. The format can be produced running the C&C supertagger with
	 * the output format: %w\t%p\t%S|\n
	 *
	 * Example: Pierre NNP 0 N/N 0.99525070603732 N 0.0026450007306822|Vinken NNP 0 N 0.70743834018551 S/S...
	 */

	private static class GoldInputReader extends InputReader {

		@Override
		public InputToParser readInput(final String line) {
			final List<Category> result = new ArrayList<>();
			final String[] goldEntries = line.split(" ");
			final List<InputWord> words = new ArrayList<>(goldEntries.length);
			final List<List<ScoredCategory>> supertags = new ArrayList<>();
			for (final String entry : goldEntries) {
				final String[] goldFields = entry.split("\\|");

				if (goldFields[0].equals("\"")) {
					continue; // TODO quotes
				}
				if (goldFields.length < 3) {
					throw new InputMismatchException("Invalid input: expected \"word|POS|SUPERTAG\" but was: " + entry);
				}

				final String word = goldFields[0];
				final String pos = goldFields[1];
				final Category category = Category.valueOf(goldFields[2]);
				words.add(new InputWord(word, pos, null));
				result.add(category);
				supertags.add(Collections.singletonList(new ScoredCategory(category, Double.MAX_VALUE)));
			}
			return new InputToParser(words, result, supertags, false);
		}

		private GoldInputReader() {
		}
	}

	public static class SupertaggedInputReader extends InputReader {
		private final List<Category> cats;

		// Word|N=3|NP=2
		@Override
		public InputToParser readInput(final String line) {
			final List<Category> result = new ArrayList<>();
			final String[] goldEntries = line.split(" ");
			final List<InputWord> words = new ArrayList<>(goldEntries.length);
			final List<List<ScoredCategory>> supertags = new ArrayList<>();
			for (final String entry : goldEntries) {
				// final String[] goldFields = entry.split("\\|");
				final StringTokenizer tokenizer = new StringTokenizer(entry, "|");

				final String word = tokenizer.nextToken();// goldFields[0];
				// final String pos = goldFields[1];
				List<ScoredCategory> tagDist = new ArrayList<>();
				words.add(new InputWord(word));

				// final String[] tags = goldFields[2].split("\\|");
				// result.add(Category.valueOf(tags[0]));
				// for (int i = 1; i < goldFields.length; i++) {
				while (tokenizer.hasMoreTokens()) {
					final String tagAndScore = tokenizer.nextToken();
					final int equals = tagAndScore.indexOf("=");
					final Category category = Category.valueOf(tagAndScore.substring(0, equals));
					//cats.get(Integer.valueOf(tagAndScore.substring(0, equals)));

					tagDist.add(new ScoredCategory(category, Double.valueOf(tagAndScore.substring(equals + 1))));
				}
				Collections.sort(tagDist);

				final double bestScore = tagDist.get(0).getScore();
				final double threshold = 0.000001 * Math.exp(bestScore);
				for (int i = 1; i < tagDist.size(); i++) {
					if (Math.exp(tagDist.get(i).getScore()) < threshold) {
						tagDist = tagDist.subList(0, i);
						break;
					}
				}

				supertags.add(tagDist);
			}
			return new InputToParser(words, result, supertags, true);
		}

		public SupertaggedInputReader(final List<Category> cats) {
			this.cats = cats;
		}
	}

	private static class POSTaggedInputReader extends InputReader {

		@Override
		public InputToParser readInput(final String line) {
			final String[] taggedEntries = line.split(" ");
			final List<InputWord> inputWords = new ArrayList<>(taggedEntries.length);
			for (final String entry : taggedEntries) {
				final String[] taggedFields = entry.split("\\|");

				if (taggedFields.length < 2) {
					throw new InputMismatchException("Invalid input: expected \"word|POS\" but was: " + entry);
				}
				if (taggedFields[0].equals("\"")) {
					continue; // TODO quotes
				}
				inputWords.add(new InputWord(taggedFields[0], taggedFields[1], null));
			}
			return new InputToParser(inputWords, null, null, false);
		}
	}

	private static class POSandNERTaggedInputReader extends InputReader {
		@Override
		public InputToParser readInput(final String line) {
			final String[] taggedEntries = line.split(" ");
			final List<InputWord> inputWords = new ArrayList<>(taggedEntries.length);
			for (final String entry : taggedEntries) {
				final String[] taggedFields = entry.split("\\|");

				if (taggedFields[0].equals("\"")) {
					continue; // TODO quotes
				}
				if (taggedFields.length < 3) {
					throw new InputMismatchException("Invalid input: expected \"word|POS|NER\" but was: " + entry + "\n"
							+ "The C&C can produce this format using: \"bin/pos -model models/pos | bin/ner -model models/ner -ofmt \"%w|%p|%n \\n\"\"");
				}
				inputWords.add(new InputWord(taggedFields[0], taggedFields[1], taggedFields[2]));
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

	/**
	 * Runs a TensorFlow library which deals with loading and tagging the file.
	 */
	public static class TensorFlowInputReader extends InputReader {
		private final Taggerflow tagger;
		private final List<Category> categories;
		private final int maxBatchSize;
		private final Stopwatch gpuTime = Stopwatch.createUnstarted();

		public TensorFlowInputReader(final File folder, final List<Category> categories, final int maxBatchSize) {
			tagger = new Taggerflow(folder, 1e-5);
			this.categories = categories;
			this.maxBatchSize = maxBatchSize;
		}

		@Override
		public InputToParser readInput(final String line) {
			throw new UnsupportedOperationException("TensorFlowInputReader can only be used in batch mode");
		}

		public long getSupertaggingTime(final TimeUnit timeUnit) {
			return gpuTime.elapsed(timeUnit);
		}

		public void resetSupertaggingTime() {
			gpuTime.reset();
		}

		@Override
		public Iterable<InputToParser> readFile(final File file) throws IOException {
			return () -> {
				gpuTime.start();
				final Iterator<TaggedSentence> taggedSentenceIterator = tagger
						.predict(file.getAbsolutePath(), maxBatchSize).iterator();
				gpuTime.stop();

				return new Iterator<InputToParser>() {
					@Override
					public boolean hasNext() {
						return taggedSentenceIterator.hasNext();
					}

					@Override
					public InputToParser next() {
						gpuTime.start();
						final TaggedSentence sentence = taggedSentenceIterator.next();
						gpuTime.stop();
						final List<List<ScoredCategory>> tagDist = TaggerflowLSTM
								.getScoredCategories(sentence, categories);
						final List<InputWord> words = sentence.getTokenList().stream().map(TaggedToken::getWord)
								.map(InputWord::new).collect(Collectors.toList());

						if (words.size() == 0) {
							return next();// FIXME sentences not parsed by TensorFlow
						}

						Preconditions.checkState(words.size() == tagDist.size());
						return new InputToParser(words, null, tagDist, true);
					}
				};
			};
		}
	}

}
