package edu.uw.easysrl.syntax.parser;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.main.EasySRL.InputFormat;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.semantics.Lexicon;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.LogicParser;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.SeenRules;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLabelling;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeVisitor;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Scored;

public abstract class AbstractParser implements Parser {

	public AbstractParser(final Collection<Category> lexicalCategories, final int maxSentenceLength, final int nbest,
			final double nbestBeam, final InputFormat inputFormat, final List<Category> validRootCategories,
			final File modelFolder) throws IOException {
		this(lexicalCategories, maxSentenceLength, nbest, nbestBeam, inputFormat, validRootCategories, new File(
				modelFolder, "unaryRules"), new File(modelFolder, "binaryRules"), new File(modelFolder, "markedup"),
				new File(modelFolder, "seenRules"));
	}

	private AbstractParser(final Collection<Category> lexicalCategories, final int maxSentenceLength, final int nbest,
			final double nbestBeam, final InputFormat inputFormat, final List<Category> validRootCategories,
			final File unaryRulesFile, final File extraCombinatorsFile, final File markedupFile,
			final File seenRulesFile) throws IOException {
		this.maxLength = maxSentenceLength;
		this.nbest = nbest;
		this.unaryRules = loadUnaryRules(unaryRulesFile);
		this.reader = InputReader.make(inputFormat);
		this.seenRules = new SeenRules(seenRulesFile, lexicalCategories);
		this.nbestBeam = Math.log(nbestBeam);
		DependencyStructure.parseMarkedUpFile(markedupFile);

		final List<Combinator> combinators = new ArrayList<>(Combinator.STANDARD_COMBINATORS);

		if (extraCombinatorsFile != null && extraCombinatorsFile.exists()) {
			combinators.addAll(Combinator.loadSpecialCombinators(extraCombinatorsFile));
		}
		this.binaryRules = ImmutableList.copyOf(combinators);

		possibleRootCategories = ImmutableSet.copyOf(validRootCategories);
	}

	protected final int maxLength;

	protected final Collection<Combinator> binaryRules;
	protected final Multimap<Category, UnaryRule> unaryRules;
	protected final int nbest;
	protected final double nbestBeam;

	private final InputReader reader;

	protected final SeenRules seenRules;

	protected final Collection<Category> possibleRootCategories;

	private final Multimap<Integer, Long> sentenceLengthToParseTimeInNanos = HashMultimap.create();

	@Override
	public Multimap<Integer, Long> getSentenceLengthToParseTimeInNanos() {
		return sentenceLengthToParseTimeInNanos;
	}

	private final Stopwatch parsingTimeOnly = Stopwatch.createUnstarted();
	private final Stopwatch taggingTimeOnly = Stopwatch.createUnstarted();

	public final static class UnaryRule implements Serializable {
		private static final long serialVersionUID = 1L;
		private final Category result;
		/*
		 * Updates the dependencies of the child node.
		 */
		private final DependencyStructure dependencyStructureTransformation;
		private final Logic semantics;
		private final int id;

		private UnaryRule(final int id, final Category result, final DependencyStructure dependencyStructure,
				final Logic semantics) {
			this.result = result;
			this.dependencyStructureTransformation = dependencyStructure;
			this.id = id;
			this.semantics = semantics;
		}

		public UnaryRule(final int id, final String from, final String to, final Logic semantics) {
			this(id, Category.valueOf(to), DependencyStructure.makeUnaryRuleTransformation(from, to), semantics);
		}

		public int getID() {
			return id;
		}

		public Category getCategory() {
			return getResult();
		}

		public DependencyStructure getDependencyStructureTransformation() {
			return dependencyStructureTransformation;
		}

		public Category getResult() {
			return result;
		}

		public Logic apply(final Logic logic) {
			return semantics.alphaReduce().apply(logic);
		}
	}

	/**
	 * Loads file containing unary rules
	 */
	public static Multimap<Category, UnaryRule> loadUnaryRules(final File file) throws IOException {
		final Multimap<Category, UnaryRule> result = HashMultimap.create();
		final Lexicon lexicon = new Lexicon();
		for (String line : Util.readFile(file)) {
			// Allow comments.
			if (line.startsWith("#")) {
				continue;
			}
			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}

			final String[] fields = line.split("\\s+");
			if (fields.length != 2 && fields.length != 3) {
				throw new Error("Expected 2 categories (and optional logical form) on line in UnaryRule file: " + line);
			}

			final String from = fields[0];
			final String to = fields[1];
			final Category cat = Category.make(Category.valueOf(to), Slash.FWD, Category.valueOf(from));
			Logic logic;
			if (fields.length == 3) {
				logic = LogicParser.fromString(fields[2], cat);
			} else {
				logic = lexicon.getEntry(null, "NULL", cat, Coindexation.fromString(to + "/" + from));
			}
			result.put(Category.valueOf(from), new UnaryRule(result.size(), from, to, logic));
			// LogicParser.fromString(logic,Category.make(Category.valueOf(to), Slash.FWD,
			// Category.valueOf(from)))
		}

		return ImmutableMultimap.copyOf(result);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see uk.ac.ed.easyccg.syntax.ParserInterface#parse(java.lang.String)
	 */
	@Override
	public List<Scored<SyntaxTreeNode>> parse(final SuperTaggingResults results, final String line) {
		final InputToParser input = reader.readInput(line);
		final List<Scored<SyntaxTreeNode>> parses = parseSentence(results, input);
		return parses;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see uk.ac.ed.easyccg.syntax.ParserInterface#parseTokens(java.util.List)
	 */
	@Override
	public List<Scored<SyntaxTreeNode>> parseTokens(final List<String> words) {
		final InputToParser input = InputToParser.fromTokens(words);
		final List<Scored<SyntaxTreeNode>> parses = doParsing(input);

		return parses;
	}

	public static class SuperTaggingResults {
		public AtomicInteger parsedSentences = new AtomicInteger();
		public AtomicInteger totalSentences = new AtomicInteger();

		public AtomicInteger rightCats = new AtomicInteger();
		public AtomicInteger totalCats = new AtomicInteger();

		public AtomicInteger exactMatch = new AtomicInteger();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see uk.ac.ed.easyccg.syntax.ParserInterface#parseSentence(uk.ac.ed.easyccg .syntax.Parser.SuperTaggingResults,
	 * uk.ac.ed.easyccg.syntax.InputReader.InputToParser)
	 */
	@Override
	public List<Scored<SyntaxTreeNode>> parseSentence(final SuperTaggingResults results, final InputToParser input) {
		results.totalSentences.incrementAndGet();

		if (input.length() >= maxLength) {
			System.err.println("Skipping sentence of length " + input.length());
			return null;
		}

		final List<Scored<SyntaxTreeNode>> parses = doParsing(input);

		if (parses != null) {
			results.parsedSentences.incrementAndGet();

			if (input.haveGoldCategories()) {
				final List<Category> gold = input.getGoldCategories();

				int bestCorrect = -1;

				for (final Scored<SyntaxTreeNode> parse : parses) {
					final List<Category> supertags = getSupertags(parse.getObject());
					final int correct = countCorrect(gold, supertags);
					if (correct > bestCorrect) {
						bestCorrect = correct;
					}
				}

				results.rightCats.addAndGet(bestCorrect);
				results.totalCats.addAndGet(gold.size());

				if (bestCorrect == gold.size()) {
					results.exactMatch.incrementAndGet();
				}
			}

		} else {
			System.err.println("FAILED TO PARSE! " + input.getWordsAsString());
		}
		return parses;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see uk.ac.ed.easyccg.syntax.ParserInterface#doParsing(uk.ac.ed.easyccg.syntax .InputReader.InputToParser)
	 */
	@Override
	public List<Scored<SyntaxTreeNode>> doParsing(final InputToParser input) {
		if (input.length() > maxLength) {
			System.err.println("Skipping sentence of length " + input.length());
			return null;
		}

		return parseAstar(input.getInputWords());
	}

	private int countCorrect(final List<Category> gold, final List<Category> predicted) {
		int rightCats = 0;
		for (int i = 0; i < gold.size(); i++) {
			if (predicted.get(i).equals(gold.get(i))) {
				rightCats++;
			}
		}
		return rightCats;
	}

	/**
	 * Takes supertagged input and returns a set of parses.
	 *
	 * Returns null if the parse fails.
	 */
	abstract List<Scored<SyntaxTreeNode>> parseAstar(List<InputWord> sentence);

	private final Map<Category, Map<Category, Collection<RuleProduction>>> ruleCache = new HashMap<>();

	/**
	 * Returns the set of binary rule productions between these two categories.
	 */
	protected Collection<RuleProduction> getRules(final Category left, final Category right) {
		Map<Category, Collection<RuleProduction>> rightToRules = ruleCache.get(left);
		if (rightToRules == null) {
			rightToRules = new HashMap<>();
			ruleCache.put(left, rightToRules);
		}

		Collection<RuleProduction> result = rightToRules.get(right);
		if (result == null) {
			result = Combinator.getRules(left, right, binaryRules);
			rightToRules.put(right, ImmutableList.copyOf(result));
		}

		return result;
	}

	/**
	 * Converts a parse into a list of supercategories.
	 */

	private static List<Category> getSupertags(final SyntaxTreeNode parse) {
		final GetSupertagsVisitor v = new GetSupertagsVisitor();
		parse.accept(v);
		return v.result;
	}

	@Override
	public long getParsingTimeOnlyInMillis() {
		return parsingTimeOnly.elapsed(TimeUnit.MILLISECONDS);
	}

	@Override
	public long getTaggingTimeOnlyInMillis() {
		return taggingTimeOnly.elapsed(TimeUnit.MILLISECONDS);
	}

	private static class GetSupertagsVisitor implements SyntaxTreeNodeVisitor {
		List<Category> result = new ArrayList<>();

		@Override
		public void visit(final SyntaxTreeNodeBinary node) {
			node.getLeftChild().accept(this);
			node.getRightChild().accept(this);
		}

		@Override
		public void visit(final SyntaxTreeNodeUnary node) {
			node.getChild().accept(this);
		}

		@Override
		public void visit(final SyntaxTreeNodeLeaf node) {
			result.add(node.getCategory());
		}

		@Override
		public void visit(final SyntaxTreeNodeLabelling node) {
			node.getChild(0).accept(this);
		}
	}

	/**
	 * Chart Cell used for N-best parsing. It allows multiple entries with the same category, if they are not
	 * equivalent.
	 */
	protected class CellNBest extends ChartCell {
		private final Multimap<Object, AgendaItem> keyToEntries = HashMultimap.create();

		@Override
		public Collection<AgendaItem> getEntries() {
			return keyToEntries.values();
		}

		@Override
		void addEntry(final Object equivalenceClassKey, final AgendaItem newEntry) {
			keyToEntries.put(equivalenceClassKey, newEntry);
		}

		@Override
		int size() {
			return keyToEntries.size();
		}

		@Override
		boolean isFull(final AgendaItem entry) {
			return keyToEntries.get(entry.getEquivalenceClassKey()).size() > nbest;
		}
	}

	/**
	 * Chart Cell used for 1-best parsing.
	 */
	protected static class Cell1Best extends ChartCell {
		final Map<Object, AgendaItem> keyToProbability = new HashMap<>();

		@Override
		public Collection<AgendaItem> getEntries() {
			return keyToProbability.values();
		}

		@Override
		void addEntry(final Object equivalenceClassKey, final AgendaItem newEntry) {
			keyToProbability.put(equivalenceClassKey, newEntry);
		}

		@Override
		boolean isFull(final AgendaItem entry) {
			return keyToProbability.containsKey(entry.getEquivalenceClassKey());
		}

		@Override
		int size() {
			return keyToProbability.size();
		}
	}

	protected static class Cell1BestCKY extends Cell1Best {
		@Override
		public Collection<AgendaItem> getEntries() {
			return keyToProbability.values();
		}

		@Override
		boolean isFull(final AgendaItem entry) {
			final AgendaItem currentEntry = keyToProbability.get(entry.getEquivalenceClassKey());
			return currentEntry != null && currentEntry.getInsideScore() > entry.getInsideScore();
		}

	}

	protected static abstract class ChartCell {
		// private double bestValue = IMPOSSIBLE;

		public ChartCell() {
		}

		/**
		 * Possibly adds a @CellEntry to this chart cell. Returns true if the parse was added, and false if the cell was
		 * unchanged.
		 */
		public final boolean add(final AgendaItem entry) {
			// See if the cell already has enough parses with this category.
			// All existing entries are guaranteed to have a higher probability
			if (isFull(entry)) {
				return false;
			} else {
				addEntry(entry.getEquivalenceClassKey(), entry);
				return true;
			}
		}

		abstract boolean isFull(AgendaItem entry);

		public abstract Collection<AgendaItem> getEntries();

		abstract void addEntry(Object equivalenceClassKey, AgendaItem newEntry);

		abstract int size();

	}

	public int getMaxSentenceLength() {
		return maxLength;
	}
}