package edu.uw.easysrl.syntax.model;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.lemmatizer.MorphaStemmer;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.feature.ArgumentSlotFeature;
import edu.uw.easysrl.util.Util;

/**
 * Keeps track of various thresholds used for pruning.
 */
public class CutoffsDictionary implements CutoffsDictionaryInterface {

	/**
	 *
	 */
	private static final long serialVersionUID = -5507993310910756162L;
	private final int minSlotRole = 2;
	private final int minRoleDistance = 2;
	private final int minSlotSRL = 2;
	private final int maxDependencyLength;

	private final Table<Category, Integer, Multiset<SRLLabel>> categoryToArgumentToSRLs = HashBasedTable.create();
	private final Map<String, Collection<Category>> wordToCategory;
	private final Map<SRLLabel, Multiset<Integer>> srlToOffset = new HashMap<>();

	private final Set<Category> lexicalCategories = new HashSet<>();

	@Override
	public boolean isFrequent(final Category category, final int argumentNumber, final SRLLabel label) {
		if (label == SRLFrame.NONE) {
			return true;
		}
		final Multiset<SRLLabel> countForCategory = categoryToArgumentToSRLs.get(category.withoutAnnotation(),
				argumentNumber);
		return countForCategory != null && countForCategory.size() >= minSlotSRL
				&& countForCategory.count(label) >= minSlotRole;
	}

	@Override
	public boolean isFrequent(final SRLLabel label, final int offset) {
		final int count = srlToOffset.get(label).count(offset);
		return count >= minRoleDistance || offset == 0;
	}

	public CutoffsDictionary(final Collection<Category> lexicalCategories,
			final Map<String, Collection<Category>> tagDict, final int maxDependencyLength) {
		try {
			this.maxDependencyLength = maxDependencyLength;
			make();

			if (tagDict != null) {
				for (final Collection<Category> tagsForWord : tagDict.values()) {
					tagsForWord.retainAll(lexicalCategories);
				}
			}

			wordToCategory = tagDict;
			this.lexicalCategories.addAll(lexicalCategories);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void make() throws IOException {

		final Map<String, Multiset<SRLLabel>> keyToRole = new HashMap<>();

		for (final SRLLabel label : SRLFrame.getAllSrlLabels()) {
			srlToOffset.put(label, HashMultiset.create());
		}
		final Iterator<Sentence> sentences = ParallelCorpusReader.READER.readCorpus(false);
		while (sentences.hasNext()) {
			final Sentence sentence = sentences.next();
			final List<InputWord> words = sentence.getInputWords();
			final List<Category> goldCategories = sentence.getLexicalCategories();
			final Map<SRLDependency, CCGBankDependency> depMap = sentence.getCorrespondingCCGBankDependencies();

			for (int wordIndex = 0; wordIndex < sentence.getLength(); wordIndex++) {

				final Category goldCategory = goldCategories.get(wordIndex);
				for (final Entry<SRLDependency, CCGBankDependency> dep : depMap.entrySet()) {
					if (dep.getValue() != null && dep.getValue().getSentencePositionOfPredicate() == wordIndex) {
						final int offset = dep.getValue().getSentencePositionOfArgument()
								- dep.getValue().getSentencePositionOfPredicate();
						for (int i = Math.min(offset, 0); i <= Math.max(offset, 0); i++) {
							if (i != 0 && Math.abs(offset) <= maxDependencyLength) {
								// For a word at -5, also at -4,-3,-2,-1
								Util.add(srlToOffset, dep.getKey().getLabel(), i);
							}
						}
						Util.add(categoryToArgumentToSRLs, goldCategory, dep.getValue().getArgNumber(), dep.getKey()
								.getLabel());

						final Preposition preposition = Preposition.fromString(dep.getKey().getPreposition());
						final String key = makeKey(words.get(wordIndex).word, goldCategory, preposition, dep.getValue()
								.getArgNumber());
						Multiset<SRLLabel> roles = keyToRole.get(key);
						if (roles == null) {
							roles = HashMultiset.create();
							roles.add(SRLFrame.NONE);
							keyToRole.put(key, roles);
						}

						roles.add(dep.getKey().getLabel());
					}

				}

			}
		}

		for (final Entry<String, Multiset<SRLLabel>> entry : keyToRole.entrySet()) {
			this.keyToRole.put(entry.getKey(), new HashSet<>(entry.getValue()));

		}

	}

	private static String makeKey(final String word, final Category category, final Preposition preposition,
			final int argumentNumber) {
		final String lemma = MorphaStemmer.stemToken(word).toLowerCase();
		final String key = lemma + ArgumentSlotFeature.makeKey(preposition, argumentNumber, category);

		return key;
	}

	private final Map<String, Set<SRLLabel>> keyToRole = new HashMap<>();

	@Override
	public Collection<SRLLabel> getRoles(final String word, final Category category, final Preposition preposition,
			final int argumentNumber) {

		final String key = makeKey(word, category, preposition, argumentNumber);
		final Set<SRLLabel> roles = keyToRole.get(key);
		if (roles == null) {
			return SRLFrame.getAllSrlLabels();
		} else {
			return roles;
		}
	}

	@Override
	public boolean isFrequentWithAnySRLLabel(final Category category, final int argumentNumber) {

		final Multiset<SRLLabel> countForCategory = categoryToArgumentToSRLs.get(category.withoutAnnotation(),
				argumentNumber);
		if (countForCategory == null) {
			return false;
		}
		for (final com.google.common.collect.Multiset.Entry<SRLLabel> entry : countForCategory.entrySet()) {
			if (entry.getCount() > minSlotRole && entry.getElement() != SRLFrame.NONE) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Map<String, Collection<Category>> getTagDict() {
		return wordToCategory;
	}

	@Override
	public Collection<Integer> getOffsetsForLabel(final SRLLabel label) {
		return srlToOffset.get(label).elementSet();
	}

}
