package edu.uw.easysrl.util;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.tagger.Tagger;

public class BatchUtil {
	private BatchUtil() {
	}

	private static InputToParser toParserInput(List<InputWord> words,
			List<List<Tagger.ScoredCategory>> tagDistribution) {
		Preconditions.checkState(words.size() == tagDistribution.size());
		return new InputToParser(words, null, tagDistribution, true);
	}

	public static Stream<List<Util.Scored<SyntaxTreeNode>>> parseBatched(Supplier<Stream<List<InputWord>>> sentences,
			Tagger tagger, Parser parser) {
		return Util.zip(sentences.get(), tagger.tagBatch(sentences.get()), BatchUtil::toParserInput).map(parser::doParsing);
	}
}
