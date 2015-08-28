package edu.uw.easysrl.syntax.model.feature;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.feature.Feature.LexicalCategoryFeature;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;

public class DenseLexicalFeature extends LexicalCategoryFeature {

	private final transient TaggerEmbeddings tagger;

	/**
	 *
	 */
	private static final long serialVersionUID = 5203606720996573193L;

	private final int id = -1;

	public DenseLexicalFeature(final File modelFolder) throws IOException {

		this.tagger = new TaggerEmbeddings(modelFolder, 0.0, 50, null);

	}

	/**
	 * Normalizes words by lower-casing and replacing numbers with '#'/
	 */
	private final static Pattern numbers = Pattern.compile("[0-9]");

	public static String normalize(String word) {
		word = numbers.matcher(word.toLowerCase()).replaceAll("#");
		return word;
	}

	@Override
	public double getValue(final List<InputWord> sentence, final int word, final Category category) {
		return tagger.getCategoryScores(sentence, word, 1.0, Collections.singleton(category)).getOrDefault(category,
				Double.NEGATIVE_INFINITY);

	}

	Map<Category, Double> getCategoryScores(final List<InputWord> sentence, final int word,
			final Collection<Category> categories, final double weight) {
		return tagger.getCategoryScores(sentence, word, weight, categories);
	}

	@Override
	public FeatureKey getFeatureKey(final List<InputWord> inputWords, final int wordIndex, final Category category) {
		return null;
	}

	private final FeatureKey defaultKey = hash(id);

	@Override
	public FeatureKey getDefault() {
		return defaultKey;
	}

}