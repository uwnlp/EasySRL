package edu.uw.easysrl.syntax.model.feature;

import java.io.Serializable;
import java.util.List;

import edu.uw.easysrl.main.InputReader.InputWord;

public class LexicalFeature implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = 8405420478721990473L;
	private final Clustering clustering;
	private final String name;
	private final int offset;

	public LexicalFeature(final Clustering clustering, final int offset) {
		this.name = (clustering == null ? "word" : clustering.getName()) + ":o=" + offset;
		this.clustering = clustering;
		this.offset = offset;
	}

	public String getName() {
		return name;
	}

	Object getValue(final List<InputWord> sentence, final int pos) {

		final int wordIndex = pos + getOffset();
		if (wordIndex < 0 || wordIndex >= sentence.size()) {
			return "OutOfRange";
		}

		final String word = sentence.get(wordIndex).word.toLowerCase();
		if (clustering == null) {
			return word;
		} else {
			return clustering.getCluster(word);
		}
	}

	public int getOffset() {
		return offset;
	}

	public static class POSfeature extends LexicalFeature {

		private static final long serialVersionUID = 1L;

		public POSfeature(final int offset) {
			super(null, offset);
		}

		@Override
		Object getValue(final List<InputWord> sentence, final int pos) {

			final int wordIndex = pos + getOffset();
			if (wordIndex < 0 || wordIndex >= sentence.size()) {
				return "OutOfRange";
			}

			return sentence.get(wordIndex).pos;
		}
	}
}