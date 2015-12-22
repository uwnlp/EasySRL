package edu.uw.easysrl.syntax.parser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.uw.easysrl.dependencies.ResolvedDependency;
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

	public final List<CCGandSRLparse> parseTokens(final List<InputWord> tokens) {
		return parseTokens2(tagger.tag(tokens));
	}

	protected abstract List<CCGandSRLparse> parseTokens2(List<InputWord> tokens);

	public static class BackoffSRLParser extends SRLParser {
		private final SRLParser[] parsers;
		private final AtomicInteger backoffs = new AtomicInteger();

		public BackoffSRLParser(final SRLParser... parsers) {
			super(parsers[0].tagger);
			this.parsers = parsers;
		}

		@Override
		protected List<CCGandSRLparse> parseTokens2(final List<InputWord> tokens) {
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
		protected List<CCGandSRLparse> parseTokens2(final List<InputWord> tokens) {

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
		protected List<CCGandSRLparse> parseTokens2(final List<InputWord> tokens) {
			final List<Scored<SyntaxTreeNode>> parses = parser.doParsing(new InputToParser(tokens, null, null, false));
			if (parses == null) {
				return null;
			} else {
				return parses
						.stream()
						.map(x -> new CCGandSRLparse(x.getObject(), x.getObject().getAllLabelledDependencies(), tokens))
						.collect(Collectors.toList());
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
		public PipelineSRLParser(final Parser parser, final LabelClassifier classifier, final POSTagger tagger) {
			super(parser, tagger);

			this.classifier = classifier;
		}

		private final LabelClassifier classifier;

		@Override
		public List<CCGandSRLparse> parseTokens2(final List<InputWord> tokens) {

			final List<CCGandSRLparse> parse = super.parseTokens2(tokens);
			if (parse == null) {
				return null;
			}

			return parse.stream().map(x -> addDependencies(tokens, x)).collect(Collectors.toList());
		}

		private CCGandSRLparse addDependencies(final List<InputWord> tokens, final CCGandSRLparse parse) {
			final Collection<ResolvedDependency> result = new ArrayList<>();
			for (final ResolvedDependency dep : parse.getDependencyParse()) {
				if (dep.getArgumentIndex() != dep.getHead()) {
					result.add(dep.overwriteLabel(classifier.classify(dep.dropLabel(), tokens)));
				}
			}
			return new CCGandSRLparse(parse.getCcgParse(), result, tokens);
		}

	}
}
