package edu.uw.easysrl.syntax.parser;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table.Cell;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.LogicParser;
import edu.uw.easysrl.semantics.lexicon.DefaultLexicon;
import edu.uw.easysrl.semantics.lexicon.Lexicon;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.SeenRules;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Scored;

public abstract class AbstractParser implements Parser {

	final Collection<Category> lexicalCategories;

	public AbstractParser(final Collection<Category> lexicalCategories, final int maxSentenceLength, final int nbest,
			final List<Category> validRootCategories, final File modelFolder) throws IOException {
		this(lexicalCategories, maxSentenceLength, nbest, validRootCategories, new File(modelFolder, "unaryRules"),
				new File(modelFolder, "binaryRules"), new File(modelFolder, "markedup"), new File(modelFolder,
						"seenRules"));
	}

	private AbstractParser(final Collection<Category> lexicalCategories, final int maxSentenceLength, final int nbest,
			final List<Category> validRootCategories, final File unaryRulesFile, final File extraCombinatorsFile,
			final File markedupFile, final File seenRulesFile) throws IOException {
		this.maxLength = maxSentenceLength;
		this.nbest = nbest;
		this.unaryRules = loadUnaryRules(unaryRulesFile);
		this.seenRules = new SeenRules(seenRulesFile, lexicalCategories);
		this.lexicalCategories = lexicalCategories;
		Coindexation.parseMarkedUpFile(markedupFile);

		final List<Combinator> combinators = new ArrayList<>(Combinator.STANDARD_COMBINATORS);

		if (extraCombinatorsFile != null && extraCombinatorsFile.exists()) {
			combinators.addAll(Combinator.loadSpecialCombinators(extraCombinatorsFile));
		}
		this.binaryRules = ImmutableList.copyOf(combinators);

		possibleRootCategories = ImmutableSet.copyOf(validRootCategories);

		for (final Cell<Category, Category, List<RuleProduction>> entry : seenRules.ruleTable().cellSet()) {
			// Cache out all the rules in advance.
			getRules(entry.getRowKey(), entry.getColumnKey());
		}
	}

	protected final int maxLength;

	protected double nbestBeam = 0.001;
	protected final Collection<Combinator> binaryRules;
	protected final ListMultimap<Category, UnaryRule> unaryRules;
	protected final int nbest;

	protected final SeenRules seenRules;

	protected final Collection<Category> possibleRootCategories;

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

		public boolean isTypeRaising() {
			return getCategory().isTypeRaised();
		}
	}

	/**
	 * Loads file containing unary rules
	 */
	public static ListMultimap<Category, UnaryRule> loadUnaryRules(final File file) throws IOException {
		final Multimap<Category, UnaryRule> result = HashMultimap.create();
		final Lexicon lexicon = new DefaultLexicon();
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
				logic = lexicon.getEntry(null, "NULL", cat, Coindexation.fromString(to + "/" + from, -1));
			}
			result.put(Category.valueOf(from), new UnaryRule(result.size(), from, to, logic));
		}

		return ImmutableListMultimap.copyOf(result);
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
	 * @see uk.ac.ed.easyccg.syntax.ParserInterface#doParsing(uk.ac.ed.easyccg.syntax .InputReader.InputToParser)
	 */
	@Override
	public List<Scored<SyntaxTreeNode>> doParsing(final InputToParser input, boolean isEval) {
		if (input.length() > maxLength) {
			System.err.println("Skipping sentence of length " + input.length());
			return null;
		}

		return parse(input, isEval);
	}

	/**
	 * Takes supertagged input and returns a set of parses.
	 *
	 * Returns null if the parse fails.
	 */
	protected abstract List<Scored<SyntaxTreeNode>> parse(InputToParser sentence, boolean isEval);

	private final Map<Category, Map<Category, List<RuleProduction>>> ruleCache = new IdentityHashMap<>();

	/**
	 * Returns the set of binary rule productions between these two categories.
	 */
	protected List<RuleProduction> getRules(final Category left, final Category right) {
		Map<Category, List<RuleProduction>> rightToRules = ruleCache.get(left);
		if (rightToRules == null) {
			rightToRules = new IdentityHashMap<>();
			ruleCache.put(left, rightToRules);
		}

		List<RuleProduction> result = rightToRules.get(right);
		if (result == null) {
			result = Combinator.getRules(left, right, binaryRules);
			rightToRules.put(right, result);
		}

		return result;
	}

	@Override
	public int getMaxSentenceLength() {
		return maxLength;
	}

	@Override
	public Multimap<Category, UnaryRule> getUnaryRules() {
		return unaryRules;
	}
}