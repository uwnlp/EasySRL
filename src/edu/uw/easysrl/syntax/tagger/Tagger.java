package edu.uw.easysrl.syntax.tagger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Doubles;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;

public abstract class Tagger {
	private static final String ALL_CATEGORIES = "ALL_CATEGORIES";

	protected final List<Category> lexicalCategories;
	/**
	 * Number of supertags to consider for each word. Choosing 50 means it's effectively unpruned, but saves us having
	 * to sort the complete list of categories.
	 */
	protected final int maxTagsPerWord;
	/**
	 * Pruning parameter. Supertags whose probability is less than beta times the highest-probability supertag are
	 * ignored.
	 */
	protected final double beta;
	protected final Map<String, Collection<Integer>> tagDict;

	public static Tagger make(final File folder, final double beam, final int maxTagsPerWord,
			final CutoffsDictionary cutoffs) throws IOException {
		if (new File(folder, "lstm").exists()) {
			return new TaggerLSTM(folder, beam, maxTagsPerWord, cutoffs);
		} else {
			return new TaggerEmbeddings(folder, beam, maxTagsPerWord, cutoffs);
		}
	}

	public static class ScoredCategory implements Comparable<ScoredCategory> {
		private final Category category;
		private final double score;

		public ScoredCategory(final Category category, final double score) {
			super();
			this.category = category;
			this.score = score;
		}

		@Override
		public int compareTo(final ScoredCategory o) {
			return Doubles.compare(o.score, score);
		}

		public double getScore() {
			return score;
		}

		public Category getCategory() {
			return category;
		}
	}

	public Collection<Category> getLexicalCategories() {
		return Collections.unmodifiableList(lexicalCategories);
	}

	public Tagger(final CutoffsDictionary cutoffs, final double beta, final List<Category> categories,
			final int maxTagsPerWord) throws IOException {
		this.lexicalCategories = categories;

		this.beta = beta;
		this.maxTagsPerWord = maxTagsPerWord;

		int maxCategoryID = 0;
		for (final Category c : lexicalCategories) {
			maxCategoryID = Math.max(maxCategoryID, c.getID());
		}

		this.tagDict = ImmutableMap.copyOf(loadTagDictionary(cutoffs));

	}

	private Map<String, Collection<Integer>> loadTagDictionary(final CutoffsDictionary cutoffs) throws IOException {
		final Map<Category, Integer> catToIndex = new HashMap<>();

		final List<Integer> allIndices = new ArrayList<>(lexicalCategories.size());
		int index = 0;
		for (final Category c : lexicalCategories) {
			catToIndex.put(c, index);
			allIndices.add(index);
			index++;
		}

		// Load a tag dictionary
		Map<String, Collection<Category>> dict = cutoffs == null ? null : cutoffs.getTagDict();

		final Map<String, Collection<Integer>> tagDict = new HashMap<>();
		if (dict == null) {
			dict = new HashMap<>();
			dict.put(TagDict.OTHER_WORDS, lexicalCategories);
		}
		for (final Entry<String, Collection<Category>> entry : dict.entrySet()) {
			final List<Integer> catIndices = new ArrayList<>(entry.getValue().size());
			for (final Category cat : entry.getValue()) {
				catIndices.add(catToIndex.get(cat));
			}
			tagDict.put(entry.getKey(), ImmutableList.copyOf(catIndices));
		}

		tagDict.put(ALL_CATEGORIES, allIndices);

		return tagDict;
	}

	protected String translateBrackets(String word) {
		if (word.equalsIgnoreCase("-LRB-")) {
			word = "(";
		}
		if (word.equalsIgnoreCase("-RRB-")) {
			word = ")";
		}
		return word;
	}

	/**
	 * Assigned a distribution over lexical categories for a list of words. For each word in the sentence, it returns an
	 * ordered list of ScoredCategory objects representing their category assignment.
	 */
	public abstract List<List<ScoredCategory>> tag(List<InputWord> words);

	public abstract Map<Category, Double> getCategoryScores(List<InputWord> sentence, int wordIndex, double weight,
			Collection<Category> categories);

	public List<Map<Category, Double>> getCategoryScores(final List<InputWord> words, final double supertaggerWeight) {

		final List<List<ScoredCategory>> tags = tag(words);

		final List<Map<Category, Double>> result = new ArrayList<>();
		for (final List<ScoredCategory> scores : tags) {
			result.add(scores.stream().collect(
					Collectors.toMap(y -> y.getCategory(), y -> supertaggerWeight * y.getScore())));
		}

		return result;
	}
}