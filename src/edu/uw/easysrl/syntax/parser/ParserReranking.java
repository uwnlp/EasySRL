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

public class ParserReranking extends ParserAStar {
	protected final Parser baseParser;
	private final Set<IdentityWrapper<SyntaxTreeNode>> candidateSteps;

	protected ParserReranking(final Builder builder) {
		super(builder);
		this.baseParser = builder.getBaseParser();
		this.candidateSteps = new HashSet<>();
	}

	private static IdentityWrapper<SyntaxTreeNode> wrapParse(final SyntaxTreeNode node) {
		return new IdentityWrapper<>(node, SyntaxUtil::parsesEqual, SyntaxUtil::parseHash);
	}

	@Override
	protected boolean isValidStep(final SyntaxTreeNode node) {
		return candidateSteps.contains(wrapParse(node));
	}

	@Override
	protected List<Scored<SyntaxTreeNode>> parse(final InputToParser input) {
		final List<Scored<SyntaxTreeNode>> candidates = baseParser.doParsing(input);
		if (candidates == null) {
			return null;
		}
		candidateSteps.clear();
		candidates.stream()
				.map(Scored::getObject)
				.flatMap(SyntaxUtil::subtreeStream)
				.map(ParserReranking::wrapParse)
				.forEach(candidateSteps::add);
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
