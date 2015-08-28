package edu.uw.easysrl.syntax.tagger;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.uw.easysrl.main.InputReader.InputWord;

public abstract class POSTagger {

	public abstract List<InputWord> tag(List<InputWord> words);

	// private final static POSTagger STANFORD_POS = new StanfordPOSTagger(new
	// File("english-left3words-distsim.tagger"));

	private final static class StanfordPOSTagger extends POSTagger {
		private final MaxentTagger tagger;

		private StanfordPOSTagger(final File modelFile) {
			this.tagger = new MaxentTagger(modelFile.toString());
		}

		@Override
		public List<InputWord> tag(final List<InputWord> input) {
			return tagger.tagSentence(input.stream().map(w -> new Word(w.word)).collect(Collectors.toList())).stream()
					.map(w -> new InputWord(w.word(), w.tag(), null)).collect(Collectors.toList());
		}
	}

	public static POSTagger getStanfordTagger(final File file) {
		return new StanfordPOSTagger(file);
	}
}
