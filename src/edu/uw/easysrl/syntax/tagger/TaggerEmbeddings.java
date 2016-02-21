package edu.uw.easysrl.syntax.tagger;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.PatternFilenameFilter;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.util.Util;

public class TaggerEmbeddings extends Tagger {
	private final Matrix weightMatrix;
	private final Vector bias;

	private final Map<String, double[]> discreteFeatures;
	private final Map<String, double[]> embeddingsFeatures;

	private final int totalFeatures;

	/**
	 * Number of words forward/backward to use as context (so a value of 3 means the tagger looks at 3+3+1=7 words).
	 */
	private final int contextWindow = 3;

	// Special words used in the embeddings tables.
	private final static String leftPad = "*left_pad*";
	private final static String rightPad = "*right_pad*";
	private final static String unknownLower = "*unknown_lower*";
	private final static String unknownUpper = "*unknown_upper*";
	private final static String unknownSpecial = "*unknown_special*";

	private static final String capsLower = "*lower_case*";
	private static final String capsUpper = "*upper_case*";
	private final static String capitalizedPad = "*caps_pad*";
	private final static String suffixPad = "*suffix_pad*";
	private final static String unknownSuffix = "*unknown_suffix*";

	/**
	 * Indices for POS-tags, if using them as features.
	 */
	private final Map<String, Integer> posFeatures;

	/**
	 * Indices for specific words, if using them as features.
	 */
	private final Map<String, Integer> lexicalFeatures;

	private final List<Vector> weightMatrixRows;
	private final Map<Category, Integer> categoryToIndex;

	public TaggerEmbeddings(final File modelFolder, final double beta, final int maxTagsPerWord,
			final CutoffsDictionaryInterface cutoffs) throws IOException {
		super(cutoffs, beta, loadCategories(new File(modelFolder, "categories")), maxTagsPerWord);
		try {
			final FilenameFilter embeddingsFileFilter = new PatternFilenameFilter("embeddings.*");

			// If we're using POS tags or lexical features, load l.
			this.posFeatures = loadSparseFeatures(new File(modelFolder + "/postags"));
			this.lexicalFeatures = loadSparseFeatures(new File(modelFolder + "/frequentwords"));

			// Load word embeddings.
			embeddingsFeatures = loadEmbeddings(true, modelFolder.listFiles(embeddingsFileFilter));

			// Load embeddings for capitalization and suffix features.
			discreteFeatures = new HashMap<>();
			discreteFeatures.putAll(loadEmbeddings(false, new File(modelFolder, "capitals")));
			discreteFeatures.putAll(loadEmbeddings(false, new File(modelFolder, "suffix")));
			totalFeatures = (embeddingsFeatures.get(unknownLower).length + discreteFeatures.get(unknownSuffix).length
					+ discreteFeatures.get(capsLower).length + posFeatures.size() + lexicalFeatures.size())
					* (2 * contextWindow + 1);

			// Load the list of categories used by the model.
			categoryToIndex = new HashMap<>();
			for (int i = 0; i < lexicalCategories.size(); i++) {
				categoryToIndex.put(lexicalCategories.get(i), i);
			}

			// Load the weight matrix used by the classifier.
			weightMatrix = new DenseMatrix(lexicalCategories.size(), totalFeatures);
			loadMatrix(weightMatrix, new File(modelFolder, "classifier"));

			weightMatrixRows = new ArrayList<>(lexicalCategories.size());
			for (int i = 0; i < lexicalCategories.size(); i++) {
				final Vector row = new DenseVector(totalFeatures);
				for (int j = 0; j < totalFeatures; j++) {
					row.set(j, weightMatrix.get(i, j));
				}
				weightMatrixRows.add(row);
			}

			bias = new DenseVector(lexicalCategories.size());

			loadVector(bias, new File(modelFolder, "bias"));

		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Map<String, Integer> loadSparseFeatures(final File posTagFeaturesFile) throws IOException {
		Map<String, Integer> posFeatures;
		if (posTagFeaturesFile.exists()) {
			posFeatures = new HashMap<>();
			for (final String line : Util.readFile(posTagFeaturesFile)) {
				posFeatures.put(line, posFeatures.size());
			}
			posFeatures = ImmutableMap.copyOf(posFeatures);
		} else {
			posFeatures = Collections.emptyMap();
		}

		return posFeatures;
	}

	/**
	 * Loads the neural network weight matrix.
	 */
	private void loadMatrix(final Matrix matrix, final File file) throws IOException {
		final Iterator<String> lines = Util.readFileLineByLine(file);
		int row = 0;
		while (lines.hasNext()) {
			final String line = lines.next();
			final String[] fields = line.split(" ");
			for (int i = 0; i < fields.length; i++) {
				matrix.set(row, i, Double.valueOf(fields[i]));
			}

			row++;
		}
	}

	private void loadVector(final Vector vector, final File file) throws IOException {
		final Iterator<String> lines = Util.readFileLineByLine(file);
		int row = 0;
		while (lines.hasNext()) {

			final String data = lines.next();
			vector.set(row, Double.valueOf(data));
			row++;
		}
	}

	public static List<Category> loadCategories(final File catFile) throws IOException {
		return Files.lines(catFile.toPath()).map(Category::valueOf).collect(Collectors.toList());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see uk.ac.ed.easyccg.syntax.Tagger#tag(java.util.List)
	 */
	@Override
	public List<List<ScoredCategory>> tag(final List<InputWord> words) {
		final List<List<ScoredCategory>> result = new ArrayList<>(words.size());

		for (int wordIndex = 0; wordIndex < words.size(); wordIndex++) {
			result.add(getTagsForWord(getVectorForWord(words, wordIndex), words.get(wordIndex)));
		}

		return result;
	}

	private Vector getVectorForWord(final List<InputWord> words, final int wordIndex) {
		final double[] vector = new double[totalFeatures];

		int vectorIndex = 0;
		for (int sentencePosition = wordIndex - contextWindow; sentencePosition <= wordIndex
				+ contextWindow; sentencePosition++) {
			vectorIndex = addToFeatureVector(vectorIndex, vector, sentencePosition, words);

			// If using lexical features, update the vector.
			if (lexicalFeatures.size() > 0) {
				if (sentencePosition >= 0 && sentencePosition < words.size()) {
					final Integer index = lexicalFeatures.get(words.get(sentencePosition).word);
					if (index != null) {
						vector[vectorIndex + index] = 1;
					}
				}
				vectorIndex = vectorIndex + lexicalFeatures.size();
			}

			// If using POS-tag features, update the vector.
			if (posFeatures.size() > 0) {
				if (sentencePosition >= 0 && sentencePosition < words.size()) {
					vector[vectorIndex + posFeatures.get(words.get(sentencePosition).pos)] = 1;
				}

				vectorIndex = vectorIndex + posFeatures.size();
			}

		}
		// System.out.println(words.get(wordIndex).word+ " " +
		// Doubles.asList(vector));

		return new DenseVector(vector);
	}

	/**
	 * Adds the features for the word in the specified position to the vector, and returns the next empty index in the
	 * vector.
	 */
	private int addToFeatureVector(int vectorIndex, final double[] vector, final int sentencePosition,
			final List<InputWord> words) {
		final double[] embedding = getEmbedding(words, sentencePosition);
		vectorIndex = addToVector(vectorIndex, vector, embedding);
		final double[] suffix = getSuffix(words, sentencePosition);
		vectorIndex = addToVector(vectorIndex, vector, suffix);
		final double[] caps = getCapitalization(words, sentencePosition);
		vectorIndex = addToVector(vectorIndex, vector, caps);

		return vectorIndex;
	}

	private int addToVector(int index, final double[] vector, final double[] embedding) {
		System.arraycopy(embedding, 0, vector, index, embedding.length);
		index = index + embedding.length;
		return index;
	}

	/**
	 *
	 * @param normalize
	 *            If true, words are lower-cased with numbers replaced
	 * @param embeddingsFiles
	 * @return
	 * @throws IOException
	 */
	private Map<String, double[]> loadEmbeddings(final boolean normalize, final File... embeddingsFiles)
			throws IOException {
		final Map<String, double[]> embeddingsMap = new HashMap<>();
		// Allow sharded input, by allowing the embeddings to be split across
		// multiple files.
		for (final File embeddingsFile : embeddingsFiles) {
			final Iterator<String> lines = Util.readFileLineByLine(embeddingsFile);
			while (lines.hasNext()) {
				final String line = lines.next();
				// Lines have the format: word dim1 dim2 dim3 ...
				String word = line.substring(0, line.indexOf(" "));
				if (normalize) {
					word = normalize(word);
				}

				if (!embeddingsMap.containsKey(word)) {
					final String[] fields = line.split(" ");
					final double[] embeddings = new double[fields.length - 1];
					for (int i = 1; i < fields.length; i++) {
						embeddings[i - 1] = Double.valueOf(fields[i]);
					}
					embeddingsMap.put(word, embeddings);
				}
			}
		}

		return embeddingsMap;
	}

	/**
	 * Normalizes words by lower-casing and replacing numbers with '#'/
	 */
	private final static Pattern numbers = Pattern.compile("[0-9]");

	private String normalize(String word) {
		word = numbers.matcher(word.toLowerCase()).replaceAll("#");
		return word;
	}

	/**
	 * Loads the embedding for the word at the specified index in the sentence. The index is allowed to be outside the
	 * sentence range, in which case the appropriate 'padding' embedding is returned.
	 */
	private double[] getEmbedding(final List<InputWord> words, final int index) {
		if (index < 0) {
			return embeddingsFeatures.get(leftPad);
		}
		if (index >= words.size()) {
			return embeddingsFeatures.get(rightPad);
		}
		String word = words.get(index).word;

		word = translateBrackets(word);

		final double[] result = embeddingsFeatures.get(normalize(word));
		if (result == null) {
			final char firstCharacter = word.charAt(0);
			final boolean isLower = 'a' <= firstCharacter && firstCharacter <= 'z';
			final boolean isUpper = 'A' <= firstCharacter && firstCharacter <= 'Z';
			if (isLower) {
				return embeddingsFeatures.get(unknownLower);
			} else if (isUpper) {
				return embeddingsFeatures.get(unknownUpper);
			} else {
				return embeddingsFeatures.get(unknownSpecial);
			}
		}

		return result;
	}

	/**
	 * Loads the embedding for a word's 2-character suffix. The index is allowed to be outside the sentence range, in
	 * which case the appropriate 'padding' embedding is returned.
	 */
	private double[] getSuffix(final List<InputWord> words, final int index) {
		String suffix = null;
		if (index < 0 || index >= words.size()) {
			suffix = suffixPad;
		} else {
			String word = words.get(index).word;

			word = translateBrackets(word);

			if (word.length() > 1) {
				suffix = (word.substring(word.length() - 2, word.length()));
			} else {
				// Padding for words of length 1.
				suffix = ("_" + word.substring(0, 1));
			}
		}

		double[] result = discreteFeatures.get(suffix.toLowerCase());
		if (result == null) {
			result = discreteFeatures.get(unknownSuffix);
		}
		return result;
	}

	/**
	 * Loads the embedding for a word's capitalization. The index is allowed to be outside the sentence range, in which
	 * case the appropriate 'padding' embedding is returned.
	 */
	private double[] getCapitalization(final List<InputWord> words, final int index) {
		String key;
		if (index < 0 || index >= words.size()) {
			key = capitalizedPad;
		} else {
			final String word = words.get(index).word;

			final char c = word.charAt(0);
			if ('A' <= c && c <= 'Z') {
				key = capsUpper;
			} else {
				key = capsLower;
			}
		}

		return discreteFeatures.get(key);
	}

	/**
	 * weights(cat1) ... weights(cat2) ... ... bias(cat1) bias(cat2)
	 */
	public double[] getWeightVector() {
		final double[] result = new double[(totalFeatures + 1) * lexicalCategories.size()];
		int index = 0;
		for (final Vector vector : weightMatrixRows) {
			for (int i = 0; i < vector.size(); i++) {
				result[index] = vector.get(i);
				index++;
			}
		}

		for (int i = 0; i < bias.size(); i++) {
			result[index] = bias.get(i);
			index++;
		}

		return result;
	}

	/**
	 * Returns a list of @SyntaxTreeNode for this word, sorted by their probability.
	 *
	 * @param vector
	 *            A vector
	 * @param word
	 *            The word itself.
	 * @param wordIndex
	 *            The position of the word in the sentence.
	 * @return
	 * @return
	 */
	private List<ScoredCategory> getTagsForWord(final Vector vector, final InputWord word) {

		// If we're using a tag dictionary, consider those tags --- otherwise,
		// try all tags.
		Collection<Integer> possibleCategories = tagDict.get(word.word);
		if (possibleCategories == null) {
			possibleCategories = tagDict.get(TagDict.OTHER_WORDS);
		}

		return getTagsForWord(vector, possibleCategories);

	}

	private List<ScoredCategory> getTagsForWord(final Vector vector, final Collection<Integer> possibleCategories) {
		final int size = Math.min(maxTagsPerWord, possibleCategories.size());

		double bestScore = 0.0;

		List<ScoredCategory> result = new ArrayList<>(possibleCategories.size());
		for (final Integer cat : possibleCategories) {
			final double score = weightMatrixRows.get(cat).dot(vector) + bias.get(cat);
			result.add(new ScoredCategory(lexicalCategories.get(cat), score));
			bestScore = Math.max(bestScore, score);
		}

		Collections.sort(result);
		if (result.size() > size) {
			result = result.subList(0, size);
		}

		final double threshold = beta * Math.exp(bestScore);
		for (int i = 2; i < result.size(); i++) {
			// TODO binary search
			if (Math.exp(result.get(i).getScore()) < threshold) {
				result = result.subList(0, i);
				break;
			}
		}

		return result;
	}

	@Override
	public Map<Category, Double> getCategoryScores(final List<InputWord> sentence, final int wordIndex,
			final double weight, final Collection<Category> categories) {

		final List<ScoredCategory> scoredCats = getTagsForWord(getVectorForWord(sentence, wordIndex),
				categories.stream().map(x -> categoryToIndex.get(x)).collect(Collectors.toList()));
		return scoredCats.stream().collect(Collectors.toMap(ScoredCategory::getCategory, x -> x.getScore() * weight));
	}

}
