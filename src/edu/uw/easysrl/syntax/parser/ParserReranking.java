package edu.uw.easysrl.syntax.parser;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.util.IdentityWrapper;
import edu.uw.easysrl.util.SyntaxUtil;
import edu.uw.easysrl.util.Util.Scored;

/*
 * Performs reranking by getting an n-best list from a base parser and reparsing with the reranking model while
 * constraining the search to the candidates in the n-best list.
 */
public class ParserReranking extends ParserAStar {
	protected final Parser baseParser;
	private final Set<IdentityWrapper<SyntaxTreeNode>> candidateSteps;

	protected ParserReranking(final Builder builder) {
		super(builder);
		this.baseParser = builder.getBaseParser();
		this.candidateSteps = new HashSet<>();
	}

	private static IdentityWrapper<SyntaxTreeNode> wrapParse(final SyntaxTreeNode node) {
		// Maps parses to a wrapper where the parse structure is used to test equality.
		return new IdentityWrapper<>(node, SyntaxUtil::parsesEqual, SyntaxUtil::parseHash);
	}

	@Override
	protected boolean isValidStep(final SyntaxTreeNode node) {
		// Only take a step if the explored node belongs to one of the n-best parses.
		return candidateSteps.contains(wrapParse(node));
	}

	@Override
	protected List<Scored<SyntaxTreeNode>> parse(final InputToParser input) {
		final List<Scored<SyntaxTreeNode>> candidates = baseParser.doParsing(input);

		if (candidates == null) {
			// Base parser failed. Fail here too.
			return null;
		} else if (candidates.size() == 1) {
			// Base parser only provided a single parse. Nothing to rerank.
			return candidates;
		}

		// Add all partial parses to a set which constrains the search.
		candidateSteps.clear();
		candidates.stream()
				.map(Scored::getObject)
				.flatMap(SyntaxUtil::subtreeStream)
				.map(ParserReranking::wrapParse)
				.forEach(candidateSteps::add);

		// Do standard A* parsing with the constraints from the n-best list.
		return super.parse(input);
	}

	public static class Builder extends ParserAStar.Builder {
		private Parser baseParser;

		public Builder(final File modelFolder, final Parser baseParser) {
			super(modelFolder);
			this.baseParser = baseParser;
		}

		public Parser getBaseParser() {
			return baseParser;
		}

		@Override
		protected ParserReranking build2() {
			return new ParserReranking(this);
		}
	}
}
