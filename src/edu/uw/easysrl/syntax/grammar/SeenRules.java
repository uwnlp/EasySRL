package edu.uw.easysrl.syntax.grammar;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.util.Util;

/**
 * Handles filtering rules by CCGBank category combination.
 */
public class SeenRules {
	public static void main(final String[] args) throws IOException {
		makeFromCorpus(ParallelCorpusReader.READER);
	}

	private static void makeFromCorpus(final ParallelCorpusReader corpus) throws IOException {
		final Iterator<Sentence> sentences = corpus.readCorpus(false);
		final Multiset<String> result = HashMultiset.create();
		while (sentences.hasNext()) {
			final Sentence sentence = sentences.next();
			getRulesFromParse(sentence.getCcgbankParse(), result);
		}

		for (final String rule : Multisets.copyHighestCountFirst(result).elementSet()) {
			System.out.println(rule);
		}
	}

	private static void getRulesFromParse(final SyntaxTreeNode parse, final Multiset<String> result) {
		if (parse.getChildren().size() == 2) {
			result.add(parse.getChild(0).getCategory().toString() + " " + parse.getChild(1).getCategory().toString());

		}

		for (final SyntaxTreeNode child : parse.getChildren()) {
			getRulesFromParse(child, result);
		}
	}

	private final boolean[][] seen;
	private final int numberOfSeenCategories;
	private final Table<Category, Category, List<RuleProduction>> ruleTable;

	public boolean isSeen(final Category left, final Category right) {
		if (seen == null) {
			return true;
		}

		return (left.getID() < numberOfSeenCategories && right.getID() < numberOfSeenCategories && seen[left.getID()][right
				.getID()]);
	}

	private Category getCategory(String s) {
		// Convert CCGbank type-raising S/(S\NP) to S[X]/(S[X]\NP)
		final Category c = Category.valueOf(s);
		if (c.isTypeRaised() && c.isFunctionInto(Category.S)) {
			s = s.replace("S/", "S[X]/").replace("S\\", "S[X]\\");
			return Category.valueOf(s);
		}

		return c;
	}

	public SeenRules(final File file, final Collection<Category> lexicalCategories) throws IOException {
		ruleTable = HashBasedTable.create();
		if (file == null) {
			seen = null;
			numberOfSeenCategories = 0;
		} else if (!file.exists()) {
			System.err.println("No 'seenRules' file available for model. Allowing all CCG-legal rules.");
			seen = null;
			numberOfSeenCategories = 0;
		} else {
			// final Table<Category, Category, List<RuleProduction>> tab = HashBasedTable.create();

			int maxID = 0;
			// Hack way of dealing with conjunctions of declarative and embedded sentences:
			// "He said he'll win and that she'll lose"
			maxID = addToTable(ruleTable, maxID, Category.Sdcl, Category.valueOf("S[em]\\S[em]"));
			final Set<Category> categories = new HashSet<>();
			for (final String line : Util.readFile(file)) {
				// Assumes the file has the format:
				// cat1 cat2
				if (!line.startsWith("#") && !line.isEmpty()) {
					final String[] fields = line.split(" ");
					final Category left = getCategory(fields[0]);
					final Category right = getCategory(fields[1]);

					maxID = addToTable(ruleTable, maxID, left, right);
					categories.add(left);
					categories.add(right);
				}
			}

			final Set<Category> punctatation = new HashSet<>();
			for (final Category category : lexicalCategories) {
				if (category.isPunctuation()) {
					punctatation.add(category);
				}
			}

			for (final Category category : lexicalCategories) {

				// Let punctuation combine with anything.
				for (final Category punct : punctatation) {
					maxID = addToTable(ruleTable, maxID, punct, category);
					maxID = addToTable(ruleTable, maxID, category, punct);
				}

				// Add in default application seen rules. Useful for new
				// categories created by rebanking.

				if (category.isFunctor()) {
					if (category.getSlash() == Slash.FWD) {
						maxID = addToTable(ruleTable, maxID, category, category.getRight());
					} else if (category.getSlash() == Slash.BWD) {
						maxID = addToTable(ruleTable, maxID, category.getRight(), category);
					}
				}

			}

			seen = new boolean[maxID + 1][maxID + 1];

			for (final Cell<Category, Category, List<RuleProduction>> entry : ruleTable.cellSet()) {
				if (!entry.getValue().isEmpty()) {
					seen[entry.getRowKey().getID()][entry.getColumnKey().getID()] = true;
				}
			}

			numberOfSeenCategories = seen.length;
		}
	}

	private int addToTable(final Table<Category, Category, List<RuleProduction>> tab, int maxID, final Category left,
			final Category right) {
		// Check if any rules can apply to this pair of categories.
		List<RuleProduction> combinators = Combinator.getRules(left, right, Combinator.STANDARD_COMBINATORS);
		if (combinators.size() == 0) {
			return maxID;
		} else if (combinators.size() == 1) {
			combinators = Collections.singletonList(combinators.get(0));
		}
		maxID = Math.max(left.getID(), maxID);
		maxID = Math.max(right.getID(), maxID);
		tab.put(left, right, combinators);
		return maxID;
	}

	public Table<Category, Category, List<RuleProduction>> ruleTable() {
		return ruleTable;
	}
}