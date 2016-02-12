package edu.uw.easysrl.syntax.tagger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import edu.uw.Taggerflow;
import edu.uw.TaggerflowProtos.SparseValue;
import edu.uw.TaggerflowProtos.TaggedSentence;
import edu.uw.TaggerflowProtos.TaggedToken;
import edu.uw.TaggerflowProtos.TaggingInput;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;

public class TaggerflowLSTM extends Tagger {
	private final Taggerflow tagger;

	TaggerflowLSTM(final File modelFolder, final double beta, final int maxTagsPerWord, final CutoffsDictionaryInterface cutoffs)
			throws IOException {

		this(makeTaggerflow(modelFolder), beta, loadCategories(modelFolder), maxTagsPerWord, cutoffs);
	}

	private static Taggerflow makeTaggerflow(final File modelFolder) {
		// Apparently this is the easiest way to set the library path in code...
		System.setProperty("java.library.path", "lib");
		Field fieldSysPath;
		try {
			fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		File taggerflowModelFolder = new File(modelFolder, "taggerflow");
		return new Taggerflow(new File(taggerflowModelFolder, "graph.pb").getAbsolutePath(), taggerflowModelFolder.getAbsolutePath());
	}
	
	private static List<Category> loadCategories(final File modelFolder) throws IOException {
		return Files.lines(new File(modelFolder, "categories").toPath())
				.map(Category::valueOf)
				.collect(Collectors.toList());
	}

	private final List<Integer> possibleCategories = new ArrayList<>();

	public TaggerflowLSTM(final Taggerflow tagger, final double beta, List<Category> categories, final int maxTagsPerWord,
			final CutoffsDictionaryInterface cutoffs) throws IOException {
		super(cutoffs, beta, categories, maxTagsPerWord);
		this.tagger = tagger;

		for (int i = 0; i < super.lexicalCategories.size(); i++) {
			possibleCategories.add(i);
		}

	}
	
	public static List<List<ScoredCategory>> getScoredCategories(TaggedSentence sentence, List<Category> categories) {
		final List<List<ScoredCategory>> tagDistribution = new ArrayList<>(sentence.getTokenCount());
		for (TaggedToken token : sentence.getTokenList()) {
			final List<ScoredCategory> tagsForWord = new ArrayList<>(
					token.getProbabilityCount());
			int maxIndex = -1;
			double maxScore = -Double.MAX_VALUE;

			for (int k = 0; k < token.getProbabilityCount(); k++) {
				final SparseValue indexedScore = token.getProbability(k);
				final double logScore = Math
						.log(indexedScore.getValue());

				if (logScore > maxScore) {
					maxIndex = k;
					maxScore = logScore;
				}

				tagsForWord.add(new ScoredCategory(
						categories.get(indexedScore.getIndex()),
						logScore));
			}

			if (maxIndex >= 0) {
				// The parser expects the first supertag in
				// the list to be the highest scoring one,
				// so swap them. Pretty hacky...
				final ScoredCategory first = tagsForWord
						.get(0);
				tagsForWord.set(0,
						tagsForWord.get(maxIndex));
				tagsForWord.set(maxIndex, first);
			}
			tagDistribution.add(tagsForWord);
		}
		return tagDistribution;
	}

	@Override
	public List<List<ScoredCategory>> tag(final List<InputWord> words) {
		TaggingInput.Builder input = TaggingInput.newBuilder();
		input.addSentenceBuilder().addAllWord(() -> words.stream().map(x -> translateBrackets(x.word)).iterator());
		return getScoredCategories(tagger.predict(input.build()).getSentence(0), lexicalCategories);
	}

	@Override
	public Map<Category, Double> getCategoryScores(final List<InputWord> sentence, final int wordIndex,
			final double weight, final Collection<Category> categories) {
		throw new RuntimeException("TODO");
	}
}
