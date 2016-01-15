package edu.uw.easysrl.corpora;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.rebanking.Rebanker;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.util.Util;

/**
 * Code for working with CCGBank-style dependencies.
 */
public class CCGBankDependencies implements Serializable {
	public enum Partition {
		DEV, TRAIN, TEST
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private final static File candcFolder = new File("/disk/data2/s1049478/my_parser/candc"); // TODO

	/**
	 * Runs the C&C generate program on a list of parses for a sentence, and returns the output.
	 */
	public static String getDependenciesAsString(final List<SyntaxTreeNode> parses, final int id) {

		try {
			final File candcScripts = new File(candcFolder, "/src/scripts/ccg");

			final File catsFolder = new File(candcFolder, "/src/data/ccg/cats/");
			final File markedUpCategories = new File(catsFolder, "/markedup");

			final File autoFile = File.createTempFile("parse", ".auto");
			final File convertedFile = File.createTempFile("parse", ".auto2");

			final String autoString = ParsePrinter.CCGBANK_PRINTER.print(parses, id);
			Util.writeStringToFile(autoString, autoFile);
			final String command = "cat \"" + autoFile + "\" | " + candcScripts + "/convert_auto " + autoFile
					+ " | sed -f " + candcScripts + "/convert_brackets > " + convertedFile;
			final String command2 = candcFolder + "/bin/generate -j  " + catsFolder + " " + markedUpCategories + " "
					+ convertedFile;
			Util.executeCommand(command);
			final String deps = Util.executeCommand(command2);

			autoFile.delete();
			convertedFile.delete();

			return deps;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Runs the C&C generate program on a list of parses for a sentence, and returns the output.
	 *
	 * Failed parses are represented as 'null'.
	 */
	static List<DependencyParse> getDependencies(final List<SyntaxTreeNode> parses, final int id) {

		final String output = getDependenciesAsString(parses, id);
		final List<String> lines = Arrays.asList(output.split("\n", -1));
		if (lines.size() == 2) {
			return Arrays.asList((CCGBankDependencies.DependencyParse) null);
		}

		final List<DependencyParse> result = new ArrayList<>();
		// Skip the C&C header.
		final Iterator<String> linesIt = lines.subList(3, lines.size()).iterator();
		final Iterator<SyntaxTreeNode> parseIt = parses.iterator();

		while (linesIt.hasNext() && parseIt.hasNext()) {
			if (!parseIt.hasNext()) {
				throw new RuntimeException("More dependency parses than input");
			}
			result.add(getDependencyParseCandC(linesIt, parseIt.next().getLeaves(), id));
		}

		if (result.size() != parses.size()) {
			throw new RuntimeException("Error in oracle parses");
		}
		return result;
	}

	/**
	 * Represents a CCGBank dependency.
	 */
	public static class CCGBankDependency implements Serializable {

		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final SyntaxTreeNodeLeaf parent;
		private final SyntaxTreeNodeLeaf child;
		private final int argumentNumber;
		private final int sentencePositionOfPredicate;

		public int getSentencePositionOfPredicate() {
			return sentencePositionOfPredicate;
		}

		public int getSentencePositionOfArgument() {
			return sentencePositionOfArgument;
		}

		private final int sentencePositionOfArgument;

		private CCGBankDependency(final SyntaxTreeNodeLeaf parent, final SyntaxTreeNodeLeaf child, final int index,
				final int sentencePositionOfPredicate, final int sentencePositionOfArgument) {
			this.parent = parent;
			this.child = child;
			this.argumentNumber = index;
			this.sentencePositionOfPredicate = sentencePositionOfPredicate;
			this.sentencePositionOfArgument = sentencePositionOfArgument;
		}

		public SyntaxTreeNodeLeaf getParent() {
			return parent;
		}

		public SyntaxTreeNodeLeaf getChild() {
			return child;
		}

		public int getArgNumber() {
			return argumentNumber;
		}

		@Override
		public String toString() {
			return getParent().getWord() + "-->" + getChild().getWord();
		}

		public Category getCategory() {
			return parent.getCategory();
		}

		public String getPredicateWord() {
			return getParent().getWord();
		}

		public String getArgumentWord() {
			return getChild().getWord();
		}
	}

	/**
	 * Represents a complete dependency parse of a sentence.
	 */
	public static class DependencyParse implements Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final Table<SyntaxTreeNodeLeaf, Integer, Collection<CCGBankDependency>> arguments = HashBasedTable
				.create();
		private final Multimap<SyntaxTreeNodeLeaf, CCGBankDependency> argumentToPredicates = HashMultimap.create();
		private final Multimap<SyntaxTreeNodeLeaf, CCGBankDependency> predicateToArguments = HashMultimap.create();
		private final Collection<CCGBankDependency> allDependencies = new HashSet<>();
		private final Table<Integer, Integer, CCGBankDependency> headToChildIndices = HashBasedTable.create();
		private final List<InputWord> words;

		private final String file;
		private final int sentenceNumber;
		private final int sentenceLength;
		private final List<SyntaxTreeNodeLeaf> leaves;

		private DependencyParse(final String file, final int sentenceNumber, final List<InputWord> words,
				final List<SyntaxTreeNodeLeaf> leaves) {
			this.file = file;
			this.sentenceNumber = sentenceNumber;
			this.sentenceLength = words.size();
			this.words = words;
			this.leaves = leaves;
		}

		private void addDependency(final CCGBankDependency dep) {
			Collection<CCGBankDependency> args = arguments.get(dep.parent, dep.getArgNumber());
			if (args == null) {
				args = new ArrayList<>();
				arguments.put(dep.parent, dep.getArgNumber(), args);
			}

			args.add(dep);

			argumentToPredicates.put(dep.getChild(), dep);
			predicateToArguments.put(dep.getParent(), dep);
			allDependencies.add(dep);
			headToChildIndices.put(dep.parent.getSentencePosition(), dep.child.getSentencePosition(), dep);
		}

		public Collection<CCGBankDependency> getDependencies(final int predicateIndex) {
			final Collection<CCGBankDependency> result = ImmutableSet.copyOf(headToChildIndices.row(predicateIndex)
					.values());
			return result;
		}

		public Collection<CCGBankDependency> getDependencies() {
			return allDependencies;
		}

		public Collection<ResolvedDependency> getResolvedDependencies() {
			return allDependencies
					.stream()
					.map(x -> new ResolvedDependency(x.sentencePositionOfPredicate, leaves.get(
							x.sentencePositionOfPredicate).getCategory(), x.argumentNumber,
							x.sentencePositionOfArgument, SRLLabel.fromString("ARG" + (x.argumentNumber - 1)),
							getPrepostion(x))).collect(Collectors.toList());
		}

		private Preposition getPrepostion(final CCGBankDependency dep) {
			Preposition preposition;
			if (dep.getCategory().getArgument(dep.getArgNumber()) == Category.PP) {
				// Prepositons are the heads of PP's in CCGBank v1. Hmm.
				preposition = Preposition.fromString(dep.getArgumentWord());

			} else {
				preposition = Preposition.NONE;
			}

			return preposition;
		}

		public List<SyntaxTreeNodeLeaf> getLeaves() {
			return leaves;
		}

		public boolean containsDependency(final int i, final int j) {
			return headToChildIndices.contains(i, j);
		}

		CCGBankDependency getDependency(final int headIndex, final int childIndex) {
			return headToChildIndices.get(headIndex, childIndex);
		}

		public String getFile() {
			return file;
		}

		public int getSentenceNumber() {
			return sentenceNumber;
		}

		public int getSentenceLength() {
			return sentenceLength;
		}

		public List<InputWord> getWords() {
			return words;
		}

		public Collection<CCGBankDependency> getArgument(final int headIndex, final int argumentNumber) {
			final Collection<CCGBankDependency> result = new ArrayList<>();
			for (final CCGBankDependency dep : getDependencies(headIndex)) {
				if (dep.getArgNumber() == argumentNumber) {
					result.add(dep);
				}
			}

			return result;
		}
	}

	/**
	 * Builds a DependencyParse, from C&C 'deps' output.
	 *
	 * 'lines' is an iterator over input lines. The function reads one parse from this iterator.
	 *
	 * @param id
	 */
	private static DependencyParse getDependencyParseCandC(final Iterator<String> lines,
			final List<SyntaxTreeNodeLeaf> supertags, final int id) {
		// Pierre_1 N/N 1 Vinken_2
		final DependencyParse result = new DependencyParse("", id, InputWord.fromLeaves(supertags), supertags);

		while (lines.hasNext()) {
			final String line = lines.next();

			if (line.isEmpty()) {
				if (result.getDependencies().size() == 0) {
					return null;
				}
				break;
			}

			final String[] fields = line.split(" ");
			final int predicate = Integer.valueOf(fields[0].substring(fields[0].indexOf('_') + 1));
			final int argIndex = Integer.valueOf(fields[2].trim());
			final int argument = Integer.valueOf(fields[3].substring(fields[3].indexOf('_') + 1));
			result.addDependency(new CCGBankDependency(supertags.get(predicate - 1), supertags.get(argument - 1),
					argIndex, predicate, argument));
		}

		return result;
	}

	public static List<DependencyParse> loadCorpus(final File folder, final Partition partition) throws IOException {
		final String regex = partition == Partition.DEV ? CCGBankParseReader.devRegex
				: partition == Partition.TRAIN ? CCGBankParseReader.trainRegex : CCGBankParseReader.testRegex;
		final List<File> autoFiles = Util.findAllFiles(folder, regex + ".*.auto");
		Collections.sort(autoFiles);

		final List<File> pargFiles = Util.findAllFiles(folder, regex + ".*.parg");
		Collections.sort(pargFiles);

		if (pargFiles.size() == 0) {
			return null;
		}

		final List<DependencyParse> result = new ArrayList<>();
		// Preconditions.checkArgument(pargFiles.size() == autoFiles.size());
		for (int i = 0; i < autoFiles.size(); i++) {

			final File autoFile = autoFiles.get(i);
			final File pargFile = pargFiles.get(i);

			Preconditions.checkState(
					Files.getNameWithoutExtension(autoFile.getName()).equals(
							Files.getNameWithoutExtension(pargFile.getName())),
							autoFile.getName() + " vs. " + pargFile.getName());

			result.addAll(getDependencyParses(autoFile, pargFile));
		}

		return result;
	}

	private static List<DependencyParse> getDependencyParses(final File autoFile, final File pargFile)
			throws IOException {
		final Iterator<String> autoLines = Util.readFileLineByLine(autoFile);
		final Iterator<String> pargLines = Util.readFileLineByLine(pargFile);
		final List<DependencyParse> result = new ArrayList<>();

		while (autoLines.hasNext()) {

			final SyntaxTreeNode autoParse = Rebanker.getParse(autoLines);
			final DependencyParse depParse = CCGBankDependencies.getDependencyParseCCGBank(pargLines,
					autoParse.getLeaves());
			result.add(depParse);
		}

		return result;
	}

	/**
	 * Builds a DependencyParse, from CCGBank PARG format.
	 *
	 * 'lines' is an iterator over input lines. The function reads one parse from this iterator.
	 */
	private static DependencyParse getDependencyParseCCGBank(final Iterator<String> lines,
			final List<SyntaxTreeNodeLeaf> words) {

		// Header line
		String line = lines.next();

		final int quote1 = line.indexOf("\"");
		final int quote2 = line.indexOf("\"", quote1 + 1);
		final int period = line.indexOf(".");
		final String file = line.substring(quote1 + 1, period);
		final int sentenceNumber = Integer.valueOf(line.substring(period + 1, quote2)) - 1;
		final DependencyParse result = new DependencyParse(file, sentenceNumber, InputWord.fromLeaves(words), words);

		while (lines.hasNext()) {
			line = lines.next();

			if (line.startsWith("<\\s>")) {
				break;
			}

			final String[] fields = line.split("\t");
			final int argument = Integer.valueOf(fields[0].trim());
			final int predicate = Integer.valueOf(fields[1].trim());
			final int argIndex = Integer.valueOf(fields[3].trim());
			result.addDependency(new CCGBankDependency(words.get(predicate), words.get(argument), argIndex, predicate,
					argument));
		}

		return result;
	}
}
