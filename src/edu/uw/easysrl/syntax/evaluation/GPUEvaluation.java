package edu.uw.easysrl.syntax.evaluation;

import com.google.common.base.Stopwatch;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.TensorFlowInputReader;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Scored;

public class GPUEvaluation {

	public static void main(final String[] args) throws IOException {
		if (args.length != 4) {
			System.err.println("Arguments: <model_dir> <data_file> <max_batch_size> <warmup_file>");
			System.exit(1);
		}

		final FileChannel rwChannel = new RandomAccessFile("/tmp/delme.out", "rw").getChannel();
		final ByteBuffer wrBuf = rwChannel.map(FileChannel.MapMode.READ_WRITE, 0, (int) Math.pow(2, 30));

		final File pipelineFolder = Util.getFile(args[0]);
		final Parser astar = EasySRL.makeParser(pipelineFolder.getAbsolutePath(), 0.000001, ParsingAlgorithm.ASTAR,
				2500000, false, Optional.empty(), 1, 70, false);

		final TensorFlowInputReader reader = new TensorFlowInputReader(new File(pipelineFolder, "taggerflow"),
				TaggerEmbeddings.loadCategories(new File(pipelineFolder, "categories")), Integer.parseInt(args[2]));

		parseFile(astar, reader, Stopwatch.createUnstarted(), new File(args[3]), wrBuf);
		reader.resetSupertaggingTime();

		System.out.println("Starting timing");
		final Stopwatch timer = Stopwatch.createStarted();
		final Stopwatch parsingTime = Stopwatch.createUnstarted();
		int sentences = parseFile(astar, reader, parsingTime, new File(args[1]), wrBuf);

		rwChannel.close();
		final long time = timer.elapsed(TimeUnit.MILLISECONDS);
		System.out.println(sentences + " sentences in " + time + " ms");

		System.out.println("GPU time:         " + reader.getSupertaggingTime(TimeUnit.SECONDS) + "s");
		System.out.println("Parsing time:     " + parsingTime.elapsed(TimeUnit.SECONDS) + "s");
		System.out.println("Other input time: " + (timer.elapsed(TimeUnit.SECONDS)
				- reader.getSupertaggingTime(TimeUnit.SECONDS) - parsingTime.elapsed(TimeUnit.SECONDS)) + "s");
		System.out.println((1000 * sentences) / time + " sentences/second");
	}

	private static int parseFile(final Parser astar, final InputReader reader, Stopwatch parsingTime, final File file,
			final ByteBuffer wrBuf) throws IOException {
		int sentences = 0;
		for (final InputToParser input : reader.readFile(file)) {
			parsingTime.start();
			final List<Scored<SyntaxTreeNode>> parses = astar.doParsing(input);
			parsingTime.stop();

			if (parses != null) {
				wrBuf.put(parses.get(0).getObject().toString().getBytes());
			}

			sentences++;
		}
		return sentences;
	}
}
