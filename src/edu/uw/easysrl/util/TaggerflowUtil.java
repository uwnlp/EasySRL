package edu.uw.easysrl.util;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.Taggerflow;
import edu.uw.TaggerflowProtos;
import edu.uw.TaggerflowProtos.TaggedSentence;
import edu.uw.TaggerflowProtos.TaggingInput;
import edu.uw.TaggerflowProtos.TaggingInputSentence;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.TaggerflowLSTM;

public class TaggerflowUtil {
    private TaggerflowUtil() {
    }

    private static TaggingInputSentence toSentence(Stream<String> words) {
        return TaggingInputSentence.newBuilder().addAllWord(() -> words.iterator()).build();
    }

    private static TaggingInput toTaggingInput(Stream<Stream<String>> sentences) {
        return TaggingInput.newBuilder().addAllSentence(() ->
                sentences.map(TaggerflowUtil::toSentence).iterator()).build();
    }

    private static InputToParser toParserInput(TaggedSentence taggedSentence, List<Category> categories) {
        final List<List<Tagger.ScoredCategory>> tagDist = TaggerflowLSTM
                .getScoredCategories(taggedSentence, categories);
        final List<InputWord> words = taggedSentence.getTokenList().stream()
                .map(TaggerflowProtos.TaggedToken::getWord)
                .map(word -> new InputWord(word, null, null))
                .collect(Collectors.toList());
        Preconditions.checkState(words.size() == tagDist.size());
        return new InputToParser(words, null, tagDist, true);
    }

    public static Stream<List<Util.Scored<SyntaxTreeNode>>> parseBatched(Stream<Stream<String>> sentences,
                                                                         List<Category> categories,
                                                                         Taggerflow taggerflow,
                                                                         Parser parser) {
        return taggerflow.predict(toTaggingInput(sentences)).getSentenceList()
                .stream()
                .map(ts -> toParserInput(ts, categories))
                .map(parser::doParsing);
    }
}
