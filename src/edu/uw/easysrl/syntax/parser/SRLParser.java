package edu.uw.easysrl.syntax.parser;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.uw.easysrl.dependencies.DependencyGenerator;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.semantics.lexicon.Lexicon;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.training.PipelineTrainer.LabelClassifier;
import edu.uw.easysrl.util.Util.Scored;

public abstract class SRLParser {
	private final POSTagger tagger;

	private SRLParser(final POSTagger tagger) {
		this.tagger = tagger;
	}

	public final List<CCGandSRLparse> parseTokens(final InputToParser tokens) {
		return parseTokens2(tokens.isPOStagged() ? tokens : tagger.tag(tokens));
	}

	protected abstract List<CCGandSRLparse> parseTokens2(InputToParser tokens);

	public static class BackoffSRLParser extends SRLParser {
		private final SRLParser[] parsers;
		private final AtomicInteger backoffs = new AtomicInteger();

		public BackoffSRLParser(final SRLParser... parsers) {
			super(parsers[0].tagger);
			this.parsers = parsers;
		}

		@Override
		protected List<CCGandSRLparse> parseTokens2(final InputToParser tokens) {
			for (final SRLParser parser : parsers) {
				final List<CCGandSRLparse> parses = parser.parseTokens(tokens);
				if (parses != null) {
					return parses;
				} else {
					backoffs.getAndIncrement();
				}
			}

			return null;
		}

		@Override
		public int getMaxSentenceLength() {
			return parsers[parsers.length - 1].getMaxSentenceLength();
		}

	}

	public abstract int getMaxSentenceLength();

	public static class SemanticParser extends SRLParser {
		private final SRLParser parser;
		private final Lexicon lexicon;

		public SemanticParser(final SRLParser parser, final Lexicon lexicon) {
			super(parser.tagger);
			this.parser = parser;
			this.lexicon = lexicon;
		}

		@Override
		protected List<CCGandSRLparse> parseTokens2(final InputToParser tokens) {

			List<CCGandSRLparse> parse = parser.parseTokens(tokens);

			if (parse != null) {
				parse = parse.stream().map(x -> x.addSemantics(lexicon)).collect(Collectors.toList());
			}

			return parse;
		}

		@Override
		public int getMaxSentenceLength() {
			return parser.getMaxSentenceLength();
		}
	}

	public static class JointSRLParser extends SRLParser {
		private final Parser parser;

		public JointSRLParser(final Parser parser, final POSTagger tagger) {
			super(tagger);
			this.parser = parser;
		}

		@Override
		protected List<CCGandSRLparse> parseTokens2(final InputToParser tokens) {
			final List<Scored<SyntaxTreeNode>> parses = parser.doParsing(tokens);
			if (parses == null) {
				return null;
			} else {
				return parses
						.stream()
						.map(x -> new CCGandSRLparse(x.getObject(), x.getObject().getAllLabelledDependencies(), tokens
								.getInputWords())).collect(Collectors.toList());
			}
		}

		@Override
		public int getMaxSentenceLength() {
			return parser.getMaxSentenceLength();
		}
	}

	public static class CCGandSRLparse implements Serializable {
		private static final long serialVersionUID = 1L;

		private final SyntaxTreeNode ccgParse;
		private final Collection<ResolvedDependency> dependencyParse;
		private final List<InputWord> words;
		private final Table<Integer, Integer, ResolvedDependency> headToArgNumberToDependency = HashBasedTable.create();
		private final List<SyntaxTreeNodeLeaf> leaves;

		private CCGandSRLparse(final SyntaxTreeNode ccgParse, final Collection<ResolvedDependency> dependencyParse,
				final List<InputWord> words) {
			super();
			this.ccgParse = ccgParse;
			this.dependencyParse = dependencyParse;
			this.words = words;
			for (final ResolvedDependency dep : dependencyParse) {
				headToArgNumberToDependency.put(dep.getHead(), dep.getArgNumber(), dep);
			}
			this.leaves = ccgParse.getLeaves();
		}

		public SyntaxTreeNode getCcgParse() {
			return ccgParse;
		}

		public Collection<ResolvedDependency> getDependencyParse() {
			return dependencyParse;
		}

		public SyntaxTreeNodeLeaf getLeaf(final int wordIndex) {
			return leaves.get(wordIndex);
		}

		public List<ResolvedDependency> getOrderedDependenciesAtPredicateIndex(final int wordIndex) {
			final Category c = getLeaf(wordIndex).getCategory();
			final List<ResolvedDependency> result = new ArrayList<>();
			for (int i = 1; i <= c.getNumberOfArguments(); i++) {
				result.add(headToArgNumberToDependency.get(wordIndex, i));
			}
			return result;
		}

		public CCGandSRLparse addSemantics(final Lexicon lexicon) {
			return new CCGandSRLparse(ccgParse.addSemantics(lexicon, this), dependencyParse, words);
		}

	}

	public static class PipelineSRLParser extends JointSRLParser {
		private final DependencyGenerator dependencyGenerator;

		public PipelineSRLParser(final Parser parser, final LabelClassifier classifier, final POSTagger tagger)
				throws IOException {
			super(parser, tagger);

			this.dependencyGenerator = new DependencyGenerator(parser.getUnaryRules());
			this.classifier = classifier;
		}

		private final LabelClassifier classifier;

		@Override
		public List<CCGandSRLparse> parseTokens2(final InputToParser tokens) {

			final List<CCGandSRLparse> parses = super.parseTokens2(tokens);
			if (parses == null) {
				return null;
			}

			return parses.stream().map(x -> addDependencies(tokens.getInputWords(), x)).collect(Collectors.toList());
		}

		private CCGandSRLparse addDependencies(final List<InputWord> tokens, final CCGandSRLparse parse) {
			final Collection<UnlabelledDependency> unlabelledDependencies = new ArrayList<>();
			// Get the dependencies in this parse.
			final SyntaxTreeNode annotatedSyntaxTree = dependencyGenerator.generateDependencies(parse.getCcgParse(),
					unlabelledDependencies);
			final Collection<ResolvedDependency> result = new ArrayList<>();
			for (final UnlabelledDependency dep : unlabelledDependencies) {
				// Add labels to the dependencies using the classifier.
				result.addAll(dep.setLabel(classifier.classify(dep, tokens)).stream()
						.filter(x -> x.getHead() != x.getArgument()).collect(Collectors.toList()));
			}
			return new CCGandSRLparse(annotatedSyntaxTree, result, tokens);
		}
	}

	public List<CCGandSRLparse> parseTokens(final List<InputWord> words) {
		return parseTokens(new InputToParser(words, null, null, false));
	}

	/**
	 * Provides a wrapper around a syntactic parser that assigned default semantic roles.
	 */
	public static SRLParser wrapperOf(final Parser parser) {
		try {
			final LabelClassifier dummyLabelClassifier = new LabelClassifier(null) {
				private static final long serialVersionUID = 1L;

				@Override
				public SRLLabel classify(final UnlabelledDependency dep, final List<InputWord> sentence) {
					return SRLFrame.NONE;
				}
			};
			final POSTagger dummyPostagger = new POSTagger() {

				@Override
				public List<InputWord> tag(final List<InputWord> words) {
					return words;
				}
			};
			return new PipelineSRLParser(parser, dummyLabelClassifier, dummyPostagger);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
