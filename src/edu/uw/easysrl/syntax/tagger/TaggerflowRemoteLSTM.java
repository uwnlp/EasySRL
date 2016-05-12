package edu.uw.easysrl.syntax.tagger;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import edu.uw.TaggerflowProtos.TaggedSentence;
import edu.uw.TaggerflowProtos.TaggingInput;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;

public class TaggerflowRemoteLSTM extends Tagger {
	private final Socket socket;

	public TaggerflowRemoteLSTM(File modelFolder) throws IOException {
		super(null, 0.0, TaggerEmbeddings.loadCategories(new File(modelFolder, "categories")), 0);
		String server = new String(
				Files.readAllBytes(new File(new File(modelFolder, "taggerflow"), "server.txt").toPath())).trim();
		int colonIndex = server.lastIndexOf(':');
		Preconditions.checkState(colonIndex >= 0, "Invalid server: " + server);
		try {
			socket = new Socket(server.substring(0, colonIndex), Integer.parseInt(server.substring(colonIndex + 1)));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Stream<List<List<ScoredCategory>>> tagBatch(Stream<List<InputWord>> sentences) {
		try {
			final TaggingInput input = TaggingInput.newBuilder()
					.addAllSentence(() -> sentences.map(TaggerflowLSTM::wordsToSentence).iterator()).build();
			input.writeDelimitedTo(socket.getOutputStream());
			return input.getSentenceList().stream().map(s -> {
				try {
					return TaggedSentence.parseDelimitedFrom(socket.getInputStream());
				} catch (final IOException e) {
					throw new RuntimeException(e);
				}
			}).map(taggedSentence -> TaggerflowLSTM.getScoredCategories(taggedSentence, lexicalCategories));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		try {
			socket.getOutputStream().close();
			socket.getInputStream().close();
			socket.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<List<ScoredCategory>> tag(List<InputWord> words) {
		return tagBatch(Stream.of(words)).findFirst().get();
	}

	@Override
	public Map<Category, Double> getCategoryScores(List<InputReader.InputWord> sentence, int wordIndex,
			double weight, Collection<Category> categories) {
		throw new UnsupportedOperationException("Not supported yet.");
	}
}