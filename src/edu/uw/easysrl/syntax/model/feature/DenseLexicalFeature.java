package edu.uw.easysrl.syntax.model.feature;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.feature.Feature.LexicalCategoryFeature;
import edu.uw.easysrl.syntax.tagger.Tagger;

public class DenseLexicalFeature extends LexicalCategoryFeature {

	private final transient Tagger tagger;

	/**
	 *
	 */
	private static final long serialVersionUID = 5203606720996573193L;

	private final int id = -1;

	public DenseLexicalFeature(final File modelFolder, final double supertaggerBeam) throws IOException {

		this.tagger = Tagger.make(modelFolder, supertaggerBeam, 50, null);

	}

	/**
	 * Normalizes words by lower-casing and replacing numbers with '#'/
	 */
	private final static Pattern numbers = Pattern.compile("[0-9]");

	public static String normalize(String word) {
		word = numbers.matcher(word.toLowerCase()).replaceAll("#");
		return word;
	}

	/**
	 * This will be really slow if using the LSTM...
	 */
	@Override
	public double getValue(final List<InputWord> sentence, final int word, final Category category) {
		return tagger.getCategoryScores(sentence, 1.0).get(word).get(category);
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

	@Override
	public void resetDefaultIndex() {
		// Do nothing.
	}

	public List<Map<Category, Double>> getCategoryScores(final List<InputWord> words, final double supertaggerWeight) {
		return tagger.getCategoryScores(words, supertaggerWeight);
	}

}