package edu.uw.easysrl.corpora;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import edu.uw.easysrl.corpora.PennTreebank.TreebankNode;
import edu.uw.easysrl.corpora.PennTreebank.TreebankParse;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;

public class SRLParse implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private final Collection<SRLDependency> dependencies = new ArrayList<>();
	private final int sentenceLength;
	private final List<String> words;

	private final static Set<String> badlyAnnotatedArguments = new HashSet<>(Arrays.asList("ARGM-by", "ARGM-on",
			"ARGM-with", "ARGM-in", "ARGM-at", "ARGM-for", "ARGM-against"));

	public SRLParse(final List<String> words) {
		this.sentenceLength = words.size();
		this.words = words;
	}

	public SRLParse(final List<String> words, final Collection<SRLDependency> srlDeps) {
		this(words);
		add(srlDeps);
	}

	static Table<String, Integer, SRLParse> parseCorpus(final Table<String, Integer, TreebankParse> parses,
			final Iterator<String> propbank, final Iterator<String> nombank) {
		final Table<String, Integer, SRLParse> result = HashBasedTable.create();

		parseFile(parses, propbank, true, result);
		if (nombank != null) {
			parseFile(parses, nombank, false, result);
		}

		return result;
	}

	private static void parseFile(final Table<String, Integer, TreebankParse> parses, final Iterator<String> propbank,
			final boolean isPropbank, final Table<String, Integer, SRLParse> result) {
		SRLParse srlparse;
		String file;
		int number;

		while (propbank.hasNext()) {
			final String line = propbank.next();
			// wsj/00/wsj_0003.mrg 0 24 gold expose.01 p---p 24:1-rel
			// 25:2-ARG2-to 27:6-ARGM-TMP 20:1,21:1,22:1,23:1-ARG1

			final String[] fields = line.split(" ");

			file = fields[0].substring(fields[0].lastIndexOf("/") + 1, fields[0].length() - 4);
			number = Integer.valueOf(fields[1]);

			final TreebankParse treebank = parses.get(file, number);
			srlparse = result.get(file, number);
			if (srlparse == null) {
				srlparse = new SRLParse(treebank.getWords());
				result.put(file, number, srlparse);
			}

			srlparse.add(parseLine(line, treebank, isPropbank));
		}
	}

	private final Map<Integer, String> indexToFrame = new HashMap<>();
	private final Multimap<Integer, SRLDependency> indexToDep = HashMultimap.create();

	private void add(final Collection<SRLDependency> deps) {
		dependencies.addAll(deps);
		for (final SRLDependency dep : deps) {
			indexToFrame.put(dep.getPredicateIndex(), dep.getPredicate());
			indexToDep.put(dep.getPredicateIndex(), dep);
		}
	}

	public Collection<SRLDependency> getDependenciesAtPredicateIndex(final int index) {
		return indexToDep.get(index);
	}

	private static Collection<SRLDependency> parseLine(final String line, final TreebankParse treebankParse,
			final boolean isPropbank) {
		final Collection<SRLDependency> result = new ArrayList<>();
		// wsj/00/wsj_0003.mrg 14 23 gold say.01 ----- 21:1-ARG0 23:0-rel
		// 0:3*25:0-ARG1
		final int firstArg = isPropbank ? 6 : 5;

		// System.out.println(line);
		final String[] fields = line.split(" ");
		final String predicate = fields[4];

		// int predIndex = Integer.valueOf((fields[2]));
		int predIndex = -1; // Integer.valueOf((fields[6].split(":")[0]));
		for (int i = firstArg; i < fields.length; i++) {
			if (fields[i].contains("-rel")) {
				predIndex = treebankParse.getWord(Integer.valueOf((fields[i].split(":")[0]))).getStartIndex(false);
			}
		}

		for (int i = firstArg; i < fields.length; i++) {
			final String field = fields[i];
			final int endArgs = field.indexOf("-");

			TreebankNode parse = null;

			final List<Integer> argIndices = new ArrayList<>();

			for (final String indexAndLevel : field.substring(0, endArgs).split("\\*|,")) {
				if (indexAndLevel.equals("none")) {
					continue;
				}

				final String[] indexAndLevelAsArray = indexAndLevel.split(":");
				final int startIndex = Integer.valueOf(indexAndLevelAsArray[0]);
				final int level = Integer.valueOf(indexAndLevelAsArray[1]);

				parse = treebankParse.getWord(startIndex);
				for (int j = 0; j < level; j++) {
					parse = parse.getParent();
				}

				for (int j = parse.getStartIndex(false); j < parse.getEndIndex(false); j++) {
					if (j != predIndex) {
						// Don't let words be args of themselves.
						argIndices.add(j);
					}
				}
			}

			if (argIndices.size() == 0) {
				// Arguments that only point to a trace element . Think can be
				// safely ignored.
				// "It was far higher than (TRACE:ARG0) expected"
				continue;
			}

			final String argLabel = field.substring(endArgs + 1);

			if (!argLabel.equals("rel")
					// Weird stuff from NomBank.
					&& !argLabel.endsWith("REF") && !argLabel.startsWith("Support") && !argLabel.equals("ARG8")
					&& !argLabel.equals("ARG9") && !argLabel.matches(".*-H.*")) {
				if (badlyAnnotatedArguments.contains(argLabel)) {
					// Weird annotation.
					continue;
				}

				// ARG2-by
				final String preposition = argLabel.indexOf("-") == -1 ? null : argLabel.substring(argLabel
						.indexOf("-") + 1);
				result.add(new SRLDependency(predicate, predIndex, argIndices, SRLLabel.fromString(argLabel),
						(preposition != null && preposition.equals(preposition.toLowerCase())) ? preposition : null)); // deal
				// with
				// ARG2-TMP
				// etc.
			}
		}

		return result;
	}

	public Collection<SRLDependency> getDependencies() {
		return dependencies;
	}

	public int getSentenceLength() {
		return sentenceLength;
	}

	public Collection<Integer> getPredicatePositions() {
		return indexToFrame.keySet();
	}

	public List<String> getWords() {
		return words;
	}

	@Override
	public String toString() {
		final StringBuilder result = new StringBuilder();
		for (final String word : words) {
			result.append(word);
			result.append(" ");
		}

		for (final SRLDependency dep : dependencies) {
			result.append("\n");
			result.append(dep.toString(words));
		}

		return result.toString();
	}
}