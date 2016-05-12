package edu.uw.easysrl.syntax.tagger;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.Taggerflow;
import edu.uw.TaggerflowProtos.TaggedSentence;
import edu.uw.TaggerflowProtos.TaggedToken;
import edu.uw.TaggerflowProtos.TaggingInput;
import edu.uw.TaggerflowProtos.TaggingInputSentence;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.util.LibraryUtil;

public class TaggerflowLSTM extends Tagger {
	private final Taggerflow tagger;

	TaggerflowLSTM(final File modelFolder, final double beta, final int maxTagsPerWord,
			final CutoffsDictionaryInterface cutoffs) throws IOException {
		this(makeTaggerflow(modelFolder, beta), beta, TaggerEmbeddings.loadCategories(new File(modelFolder, "categories")),
				maxTagsPerWord, cutoffs);
	}

	private static Taggerflow makeTaggerflow(final File modelFolder, final double beta) {
		LibraryUtil.setLibraryPath("lib");
        return new Taggerflow(new File(modelFolder, "taggerflow"), beta);
	}

	public TaggerflowLSTM(final Taggerflow tagger, final double beta, List<Category> categories,
			final int maxTagsPerWord, final CutoffsDictionaryInterface cutoffs) throws IOException {
		super(cutoffs, beta, categories, maxTagsPerWord);
		this.tagger = tagger;
	}

	public static List<List<ScoredCategory>> getScoredCategories(TaggedSentence sentence, List<Category> categories) {
		if (sentence.getTokenList().isEmpty()) {
			return Collections.emptyList();
		}
		if (sentence.getToken(0).getScoreCount() == 0) {
			// No pruning. Distribution is in the dense representation.
			final List<List<ScoredCategory>> allScoredCategories = new ArrayList<>(sentence.getTokenList().size());
			for (final TaggedToken token : sentence.getTokenList()) {
				Preconditions.checkArgument(token.getDenseScoreCount() == categories.size());
				int maxScoringIndex = 0;
				final List<ScoredCategory> scoredCategories = new ArrayList<>(categories.size());
				for (int i = 0; i < categories.size(); i++) {
					if (token.getDenseScore(i) > token.getDenseScore(maxScoringIndex)) {
						maxScoringIndex = i;
					}
					scoredCategories.add(new ScoredCategory(categories.get(i), token.getDenseScore(i)));
				}
				// Highest scoring supertag goes first.
				ScoredCategory tempScoredCategory = scoredCategories.get(0);
				scoredCategories.set(0, scoredCategories.get(maxScoringIndex));
				scoredCategories.set(maxScoringIndex, tempScoredCategory);
				allScoredCategories.add(scoredCategories);
			}
			return allScoredCategories;
		} else {
			return sentence.getTokenList().stream().map(token -> token.getScoreList().stream()
					.map(indexedScore -> new ScoredCategory(categories.get(indexedScore.getIndex()), indexedScore.getValue())).collect(Collectors.toList())).collect(Collectors.toList());
		}
	}

	@Override
	public List<List<ScoredCategory>> tag(final List<InputWord> words) {
		return tagBatch(Stream.of(words)).findFirst().get();
	}

	public static TaggingInputSentence wordsToSentence(List<InputWord> words) {
		return TaggingInputSentence.newBuilder()
				.addAllWord(() -> words.stream().map(w -> translateBrackets(w.word)).iterator()).build();
	}

	@Override
	public Stream<List<List<ScoredCategory>>> tagBatch(Stream<List<InputWord>> sentences) {
		TaggingInput.Builder input = TaggingInput.newBuilder();
		input.addAllSentence(() -> sentences.map(TaggerflowLSTM::wordsToSentence).iterator());
		return tagger.predict(input.build())
				.map(taggedSentence -> getScoredCategories(taggedSentence, lexicalCategories));
	}

	@Override
	public Map<Category, Double> getCategoryScores(final List<InputWord> sentence, final int wordIndex,
			final double weight, final Collection<Category> categories) {
		throw new RuntimeException("TODO");
	}
}
