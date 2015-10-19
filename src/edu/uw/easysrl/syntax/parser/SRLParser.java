package edu.uw.easysrl.syntax.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.semantics.Lexicon;
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

	public final CCGandSRLparse parseTokens(final List<InputWord> tokens) {
		return parseTokens2(tagger.tag(tokens));
	}

	protected abstract CCGandSRLparse parseTokens2(List<InputWord> tokens);

	public static class BackoffSRLParser extends SRLParser {
		private final SRLParser[] parsers;
		private final AtomicInteger backoffs = new AtomicInteger();

		public BackoffSRLParser(final SRLParser... parsers) {
			super(parsers[0].tagger);
			this.parsers = parsers;
		}

		@Override
		protected CCGandSRLparse parseTokens2(final List<InputWord> tokens) {
			for (final SRLParser parser : parsers) {
				final CCGandSRLparse parses = parser.parseTokens(tokens);
				if (parses != null) {
					return parses;
				} else {
					backoffs.getAndIncrement();
				}
			}

			return null;
		}

	}

	public static class SemanticParser extends SRLParser {
		private final SRLParser parser;
		private final Lexicon lexicon;

		public SemanticParser(final SRLParser parser, final Lexicon lexicon) {
			super(parser.tagger);
			this.parser = parser;
			this.lexicon = lexicon;
		}

		@Override
		protected CCGandSRLparse parseTokens2(final List<InputWord> tokens) {

			CCGandSRLparse parse = parser.parseTokens(tokens);

			if (parse != null) {
				parse = parse.addSemantics(lexicon);
			}

			return parse;
		}
	}

	public static class JointSRLParser extends SRLParser {
		private final Parser parser;

		public JointSRLParser(final Parser parser, final POSTagger tagger) {
			super(tagger);
			this.parser = parser;
		}

		@Override
		protected CCGandSRLparse parseTokens2(final List<InputWord> tokens) {
			final List<Scored<SyntaxTreeNode>> parses = parser.doParsing(new InputToParser(tokens, null, null, false));
			if (parses == null || parses.size() == 0) {
				return null;
			} else {
				return new CCGandSRLparse(parses.get(0).getObject(), parses.get(0).getObject()
						.getAllLabelledDependencies(), tokens);
			}
		}

	}

	public static class CCGandSRLparse {
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
				headToArgNumberToDependency.put(dep.getPredicateIndex(), dep.getArgNumber(), dep);
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
		public CCGandSRLparse parseTokens2(final List<InputWord> tokens) {
			// final List<InputWord> inputWords = InputWord.listOf(tokens
			// .toArray(new String[0]));
			final Collection<ResolvedDependency> result = new ArrayList<>();
			final CCGandSRLparse parse = super.parseTokens2(tokens);
			if (parse == null) {
				return null;
			}
			for (final ResolvedDependency dep : parse.getDependencyParse()) {
				if (dep.getArgumentIndex() != dep.getPredicateIndex()) {
					result.add(dep.overwriteLabel(classifier.classify(dep.dropLabel(), tokens)));
				}
			}

			return new CCGandSRLparse(parse.getCcgParse(), result, tokens);
		}

	}
}
