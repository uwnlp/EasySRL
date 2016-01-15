package edu.uw.easysrl.syntax.tagger;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.uw.easysrl.main.EasySRL.InputFormat;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.util.Util;

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

	private final static Map<File, POSTagger> cache = new HashMap<>();

	public static POSTagger getStanfordTagger(final File file) {
		POSTagger result = cache.get(file);
		if (result == null) {
			result = new StanfordPOSTagger(file);
			cache.put(file, result);
		}
		return result;
	}

	public static void main(final String[] args) {
		final POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(args[0]));
		final InputReader reader = InputReader.make(InputFormat.TOKENIZED);

		final Scanner inputLines = new Scanner(System.in, "UTF-8");

		while (inputLines.hasNext()) {
			final String line = inputLines.nextLine();
			final List<InputWord> words = postagger.tag(reader.readInput(line).getInputWords());
			for (int i = 0; i < words.size(); i++) {
				if (i > 0) {
					System.out.print(" ");
				}

				System.out.print(words.get(i).word + "|" + words.get(i).pos);
			}
			System.out.println();
		}

		inputLines.close();
	}

	public InputToParser tag(final InputToParser input) {
		final List<InputWord> tagged = tag(input.getInputWords());
		return new InputToParser(tagged, input.getGoldCategories(), input.getInputSupertags(), input.isAlreadyTagged());
	}
}
