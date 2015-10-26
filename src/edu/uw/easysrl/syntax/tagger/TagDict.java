package edu.uw.easysrl.syntax.tagger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.util.Util;

public class TagDict {

	private static final int MIN_OCCURENCES_OF_WORD = 500;
	/**
	 * Key used in the tag dictionary for infrequent words
	 */
	public static final String OTHER_WORDS = "*other_words*";
	private final static String fileName = "tagdict";

	/**
	 * Saves a tag dictionary to the model folder
	 */
	public static void writeTagDict(final Map<String, Collection<Category>> tagDict, final File file)
			throws FileNotFoundException, UnsupportedEncodingException {
		final PrintWriter writer = new PrintWriter(file, "UTF-8");
		for (final java.util.Map.Entry<String, Collection<Category>> entry : tagDict.entrySet()) {
			writer.print(entry.getKey());
			for (final Category c : entry.getValue()) {
				writer.print("\t" + c.toString());
			}
			writer.println();
		}

		writer.close();
	}

	/**
	 * Loads a tag dictionary from the model folder
	 */
	public static Map<String, Collection<Category>> readDict(final File modelFolder,
			final Set<Category> lexicalCategories) throws IOException {
		final Map<String, Collection<Category>> result = new HashMap<>();

		// Hack so that annotation gets into the tag dict
		final Multimap<Category, Category> categoryToAnnotatedCategories = HashMultimap.create();
		for (final Category category : lexicalCategories) {
			categoryToAnnotatedCategories.put(category.withoutAnnotation(), category);
		}

		final File file = new File(modelFolder, fileName);
		loadTagDict(lexicalCategories, result, file, false, categoryToAnnotatedCategories);
		// }
		if (result.size() == 0) {
			// No tag dictionaries available
			return null;
		}

		final File ccgbankTagDict = new File(modelFolder, fileName + ".ccgbank");

		loadTagDict(lexicalCategories, result, ccgbankTagDict, true, categoryToAnnotatedCategories);

		return ImmutableMap.copyOf(result);
	}

	private static void loadTagDict(final Set<Category> lexicalCategories,
			final Map<String, Collection<Category>> result, final File file, final boolean skipIfNotPresent,
			final Multimap<Category, Category> categoryToAnnotatedCategories) throws IOException {
		if (!file.exists()) {
			return;
		}

		for (final String line : Util.readFile(file)) {
			final String[] fields = line.split("\t");
			Collection<Category> cats = result.get(fields[0]);
			if (cats == null) {
				if (skipIfNotPresent) {
					continue;
				} else {
					cats = new HashSet<>();
				}

			}
			for (int i = 1; i < fields.length; i++) {
				final Category cat = Category.valueOf(fields[i]);
				if (lexicalCategories.contains(cat)) {
					cats.addAll(categoryToAnnotatedCategories.get(cat));

				}

			}

			if (cats.size() > 0) {
				result.put(fields[0], cats);
			}
		}
	}

	private final static Comparator<Entry<Category>> comparator = new Comparator<Entry<Category>>() {
		@Override
		public int compare(final Entry<Category> arg0, final Entry<Category> arg1) {
			return arg1.getCount() - arg0.getCount();
		}
	};

	/**
	 * Finds the set of categories used for each word in a corpus
	 */
	public static Map<String, Collection<Category>> makeDict(final Iterable<InputToParser> input) {
		final Multiset<String> wordCounts = HashMultiset.create();
		final Map<String, Multiset<Category>> wordToCatToCount = new HashMap<>();

		// First, count how many times each word occurs with each category
		for (final InputToParser sentence : input) {
			for (int i = 0; i < sentence.getInputWords().size(); i++) {
				final String word = sentence.getInputWords().get(i).word;
				final Category cat = sentence.getGoldCategories().get(i);
				wordCounts.add(word);

				if (!wordToCatToCount.containsKey(word)) {
					final Multiset<Category> tmp = HashMultiset.create();
					wordToCatToCount.put(word, tmp);
				}

				wordToCatToCount.get(word).add(cat);
			}
		}

		return makeDict(wordCounts, wordToCatToCount);
	}

	private static Map<String, Collection<Category>> makeDict(final Multiset<String> wordCounts,
			final Map<String, Multiset<Category>> wordToCatToCount) {
		// Now, save off a sorted list of categories
		final Multiset<Category> countsForOtherWords = HashMultiset.create();

		final Map<String, Collection<Category>> result = new HashMap<>();
		for (final Entry<String> wordAndCount : wordCounts.entrySet()) {
			final Multiset<Category> countForCategory = wordToCatToCount.get(wordAndCount.getElement());
			if (wordAndCount.getCount() > MIN_OCCURENCES_OF_WORD) {
				// Frequent word
				addEntryForWord(countForCategory, result, wordAndCount.getElement());
			} else {
				// Group stats for all rare words together.

				for (final Entry<Category> catToCount : countForCategory.entrySet()) {
					countsForOtherWords.add(catToCount.getElement(), catToCount.getCount());
				}
			}
		}
		addEntryForWord(countsForOtherWords, result, OTHER_WORDS);

		return ImmutableMap.copyOf(result);
	}

	public static Map<String, Collection<Category>> makeDictFromParses(final Iterator<SyntaxTreeNode> input) {
		final Multiset<String> wordCounts = HashMultiset.create();
		final Map<String, Multiset<Category>> wordToCatToCount = new HashMap<>();

		int sentenceCount = 0;
		// First, count how many times each word occurs with each category
		while (input.hasNext()) {
			final SyntaxTreeNode sentence = input.next();
			final List<SyntaxTreeNodeLeaf> leaves = sentence.getLeaves();
			for (int i = 0; i < leaves.size(); i++) {
				final String word = leaves.get(i).getWord();
				final Category cat = leaves.get(i).getCategory();
				wordCounts.add(word);

				if (!wordToCatToCount.containsKey(word)) {
					final Multiset<Category> tmp = HashMultiset.create();
					wordToCatToCount.put(word, tmp);
				}

				wordToCatToCount.get(word).add(cat);
			}

			sentenceCount++;
			if (sentenceCount % 100 == 0) {
				System.out.println(sentenceCount);
			}
		}

		return makeDict(wordCounts, wordToCatToCount);
	}

	private static void addEntryForWord(final Multiset<Category> countForCategory,
			final Map<String, Collection<Category>> result, final String word) {
		final List<Entry<Category>> cats = new ArrayList<>();
		for (final Entry<Category> catToCount : countForCategory.entrySet()) {
			cats.add(catToCount);
		}
		final int totalSize = countForCategory.size();
		final int minSize = Math.floorDiv(totalSize, 1000);
		Collections.sort(cats, comparator);
		final List<Category> cats2 = new ArrayList<>();

		for (final Entry<Category> entry : cats) {
			if (entry.getCount() >= minSize) {
				cats2.add(entry.getElement());
			}
		}

		result.put(word, cats2);
	}
}
