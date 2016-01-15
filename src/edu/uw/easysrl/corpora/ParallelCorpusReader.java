package edu.uw.easysrl.corpora;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.collect.TreeBasedTable;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.CCGBankDependencies.DependencyParse;
import edu.uw.easysrl.corpora.CCGBankDependencies.Partition;
import edu.uw.easysrl.corpora.DependencyTreebank.SyntacticDependencyParse;
import edu.uw.easysrl.corpora.PennTreebank.TreebankParse;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.util.Util;

public class ParallelCorpusReader {
	private final static boolean USING_NOMBANK = false;

	private final static Properties PROPERTIES = Util.loadProperties(new File("corpora.properties"));
	public static final File PROPBANK = Util.getFile(PROPERTIES.getProperty("propbank"));
	private static final File NOMBANK = Util.getFile(PROPERTIES.getProperty("nombank"));
	private static final File WSJ = Util.getFile(PROPERTIES.getProperty("ptb"));
	public static final File CCGREBANK = Util.getFile(PROPERTIES.getProperty("ccgbank"));

	public static final File BROWN = new File(PROPERTIES.getProperty("brown"));

	public static class Sentence implements Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final SyntacticDependencyParse syntacticDependencyParse;

		Sentence(final SyntaxTreeNode ccgbankParse, final DependencyParse depParse,
				final SyntacticDependencyParse syntacticDependencyParse, final SRLParse srl,
				final TreebankParse treebankParse) {
			super();
			this.ccgbankParse = ccgbankParse;
			this.srlParse = srl;
			this.ccgbankDependencyParse = depParse;
			this.syntacticDependencyParse = syntacticDependencyParse;
			this.treebankParse = treebankParse;
		}

		public SyntaxTreeNode getCcgbankParse() {
			return ccgbankParse;
		}

		public SRLParse getSrlParse() {
			return srlParse;
		}

		public DependencyParse getCCGBankDependencyParse() {
			return ccgbankDependencyParse;
		}

		private final TreebankParse treebankParse;
		private final SyntaxTreeNode ccgbankParse;
		private final SRLParse srlParse;
		private final DependencyParse ccgbankDependencyParse;

		public boolean isConsistent() {
			return ccgbankParse.getLeaves().size() == srlParse.getSentenceLength();
		}

		public SyntacticDependencyParse getSyntacticDependencyParse() {
			return syntacticDependencyParse;
		}

		public List<String> getWords() {
			final List<String> result = new ArrayList<>();
			for (final SyntaxTreeNodeLeaf leaf : ccgbankParse.getLeaves()) {
				result.add(leaf.getWord());
			}

			return result;
		}

		public List<Category> getLexicalCategories() {
			final List<Category> result = new ArrayList<>();
			for (final SyntaxTreeNodeLeaf leaf : ccgbankParse.getLeaves()) {
				result.add(leaf.getCategory());
			}

			return result;
		}

		private List<InputWord> inputWords;

		public List<InputWord> getInputWords() {
			if (inputWords == null) {
				inputWords = new ArrayList<>(ccgbankParse.getLeaves().size());
				for (final SyntaxTreeNodeLeaf leaf : ccgbankParse.getLeaves()) {
					inputWords.add(new InputWord(leaf.getWord(), leaf.getPos(), leaf.getNER()));
				}
			}

			return inputWords;
		}

		public int getLength() {
			return ccgbankParse.getLeaves().size();
		}

		public Map<SRLDependency, CCGBankDependency> getCorrespondingCCGBankDependencies() {
			final Map<SRLDependency, CCGBankDependency> result = new HashMap<>();
			for (final SRLDependency dep : srlParse.getDependencies()) {
				final CCGBankDependency correspondingDep = ParallelCorpusReader.getCorrespondingCCGBankDependency(dep,
						ccgbankDependencyParse);
				result.put(dep, correspondingDep);
			}

			return result;
		}

		public TreebankParse getTreebankParse() {
			return treebankParse;
		}
	}

	private final File ccgbank;
	private final File propbank;
	private final File treebank;
	private final File nombank;

	public final static ParallelCorpusReader READER = new ParallelCorpusReader(CCGREBANK, PROPBANK, WSJ,
			USING_NOMBANK ? NOMBANK : null);

	private ParallelCorpusReader(final File ccgbank, final File propbank, final File treebank, final File nombank) {
		super();
		this.ccgbank = ccgbank;
		this.propbank = propbank;
		this.treebank = treebank;
		this.nombank = nombank;
	}

	public static Collection<SRLParse> getPropBank00() throws IOException {
		return getPropbankSection("00");

	}

	private static Collection<SRLParse> getPropbankSection(final String section) throws IOException {
		final Table<String, Integer, TreebankParse> PTB = new PennTreebank().readCorpus(WSJ);
		final Table<String, Integer, SRLParse> srlParses = SRLParse.parseCorpus(PTB,
				Util.readFileLineByLine(new File(PROPBANK, "prop.txt")),
				USING_NOMBANK ? Util.readFileLineByLine(NOMBANK) : null);

		final Table<String, Integer, SRLParse> goldParses = TreeBasedTable.create();
		for (final Cell<String, Integer, TreebankParse> cell : PTB.cellSet()) {

			// Propbank files skip sentences with no SRL deps. Add a default
			// empty parse for all sentences.
			goldParses.put(cell.getRowKey(), cell.getColumnKey(), new SRLParse(cell.getValue().getWords()));
		}
		goldParses.putAll(srlParses);

		final Collection<SRLParse> result = new ArrayList<>();
		for (final Cell<String, Integer, SRLParse> entry : goldParses.cellSet()) {
			if (entry.getRowKey().startsWith("wsj_" + section)) {
				result.add(entry.getValue());
			}
		}
		return result;
	}

	private static List<SyntaxTreeNode> parsesTrain;
	private static List<DependencyParse> depParsesTrain;

	private static List<SyntaxTreeNode> parsesDev;
	private static List<DependencyParse> depParsesDev;

	private Table<String, Integer, TreebankParse> PTB;
	private Table<String, Integer, SyntacticDependencyParse> CoNLL;
	private Table<String, Integer, SRLParse> srlParses;

	public Iterator<Sentence> readCorpus(final boolean isDev) throws IOException {
		synchronized (this) {
			if (PTB == null) {
				PTB = new PennTreebank().readCorpus(treebank);
			}
			if (CoNLL == null) {
				CoNLL = new DependencyTreebank().readCorpus(treebank);
			}
			if (srlParses == null) {
				srlParses = SRLParse.parseCorpus(PTB, Util.readFileLineByLine(new File(propbank, "prop.txt")),
						nombank != null ? Util.readFileLineByLine(nombank) : null);
			}

		}

		List<SyntaxTreeNode> parses;
		List<DependencyParse> depParses;
		if (isDev) {
			synchronized (ccgbank) {
				if (parsesDev == null) {
					parsesDev = CCGBankParseReader.loadCorpus(ccgbank, true);
				}
				if (depParsesDev == null) {
					depParsesDev = CCGBankDependencies.loadCorpus(ccgbank, Partition.DEV);
				}
				parses = parsesDev;
				depParses = depParsesDev;

			}
		} else {
			synchronized (ccgbank) {
				if (parsesTrain == null) {
					parsesTrain = CCGBankParseReader.loadCorpus(ccgbank, false);
				}
				if (depParsesTrain == null) {
					depParsesTrain = CCGBankDependencies.loadCorpus(ccgbank, Partition.TRAIN);
				}
				parses = parsesTrain;
				depParses = depParsesTrain;
			}
		}

		// System.out.println("Total CCGBank: " + parses.size());
		// System.out.println("Total PTB: " + PTB.size());
		return new Iterator<Sentence>() {
			int i = 0;

			@Override
			public boolean hasNext() {
				return i < parses.size();
			}

			@Override
			public Sentence next() {
				final SyntaxTreeNode parse = parses.get(i);
				final DependencyParse depParse = depParses.get(i);
				SRLParse srl = srlParses.get(depParse.getFile(), depParse.getSentenceNumber());
				final SyntacticDependencyParse conll = CoNLL.get(depParse.getFile(), depParse.getSentenceNumber());
				i++;

				if (srl == null) {
					final List<String> words = getWords(parse);
					srl = new SRLParse(words);
				}

				if (depParse.getSentenceLength() != srl.getSentenceLength()) {
					return next();
				}

				return new Sentence(parse, depParse, conll, srl, PTB.get(depParse.getFile(),
						depParse.getSentenceNumber()));
			}

			private List<String> getWords(final SyntaxTreeNode parse) {
				final List<String> words = new ArrayList<>();
				for (final SyntaxTreeNodeLeaf leaf : parse.getLeaves()) {
					words.add(leaf.getWord());
				}
				return words;
			}
		};
	}

	private static CCGBankDependency getCorrespondingCCGBankDependency(final SRLDependency srlDependency,
			final DependencyParse ccgbankDependencies) {
		for (final int argIndex : srlDependency.getArgumentPositions()) {
			final CCGBankDependency dep = ccgbankDependencies.getDependency(
					srlDependency.isCoreArgument() ? srlDependency.getPredicateIndex() : argIndex,
					srlDependency.isCoreArgument() ? argIndex : srlDependency.getPredicateIndex());
			if (dep != null) {
				return dep;
			}

		}

		return null;
	}

	public static Collection<SRLParse> getPropBank23() throws IOException {
		return getPropbankSection("23");

	}

}
