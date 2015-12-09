package edu.uw.easysrl.semantics.lexicon;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.LogicParser;
import edu.uw.easysrl.semantics.SemanticType;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.util.Util;

public class ManualLexicon extends Lexicon {

	private static final String ALL_WORDS = "*ALL*";
	private final Table<String, Category, Logic> manualLexicon;

	public ManualLexicon(final File lexiconFile) throws IOException {
		this(ImmutableTable.copyOf(loadLexicon(lexiconFile)));
	}

	private ManualLexicon(final Table<String, Category, Logic> manualLexicon) {
		this.manualLexicon = manualLexicon;
	}

	@Override
	public Logic getEntry(final String word, final String pos, final Category category, final Coindexation coindexation,
			final Optional<CCGandSRLparse> parse, final int wordIndex) {
		final String lemma = getLemma(word, pos, parse, wordIndex);

		// First, see if the user-defined lexicon file has an entry for this word+category
		final Logic manualLexiconResult = getManualLexicalEntry(lemma, category);
		if (manualLexiconResult != null) {
			return manualLexiconResult;
		}
		return null;
	}

	@Override
	public boolean isMultiWordExpression(final SyntaxTreeNode node) {
		if (getManualLexicalEntry(node.getWord(), node.getCategory()) != null) {
			// Multiword expressions
			return true;
		}
		return false;
	}

	private Logic getManualLexicalEntry(final String word, final Category category) {
		Logic result = manualLexicon.get(word, category);
		if (result == null) {
			result = manualLexicon.get(ALL_WORDS, category);
		}

		// Alpha-reduce in case this lexical entry is used twice in the sentence.
		return result == null ? result : result.alphaReduce();
	}

	/**
	 * Load a manually defined lexicon
	 */
	private static Table<String, Category, Logic> loadLexicon(final File file) throws IOException {
		final Table<String, Category, Logic> result = HashBasedTable.create();
		for (final String line2 : Util.readFile(file)) {
			final int commentIndex = line2.indexOf("//");
			final String line = (commentIndex > -1 ? line2.substring(0, commentIndex) : line2).trim();

			if (line.isEmpty()) {
				continue;
			}
			final String[] fields = line.split("\t+");
			if (fields.length < 2) {
				throw new IllegalArgumentException("Must be at least two tab-separated fields on line: \"" + line2
						+ "\" in file: " + file.getPath());
			}

			final Category category;
			try {
				category = Category.valueOf(fields[0]);
			} catch (final Exception e) {
				throw new IllegalArgumentException("Unable to interpret category: \"" + fields[0] + "\" on line \""
						+ line2 + "\" in file: " + file.getPath());
			}
			final Logic logic;
			try {
				logic = LogicParser.fromString(fields[1], category);
			} catch (final Exception e) {
				throw new IllegalArgumentException("Unable to interpret semantics: \"" + fields[1] + "\" on line \""
						+ line2 + "\" in file: " + file.getPath());
			}

			if (SemanticType.makeFromCategory(category) != logic.getType()) {
				throw new IllegalArgumentException("Mismatch between syntactic and semantic type. " + category
						+ " has type: " + SemanticType.makeFromCategory(category) + " but " + logic + " has type: "
						+ logic.getType());
			}

			if (fields.length == 2) {
				result.put(ALL_WORDS, category, logic);
			} else {
				for (int i = 2; i < fields.length; i++) {
					result.put(fields[i].replaceAll("-", " "), category, logic);
				}
			}
		}

		return result;
	}

}
