package edu.uw.easysrl.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;
import uk.co.flamingpenguin.jewel.cli.Option;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;

import edu.uw.easysrl.corpora.CCGBankParseReader;
import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.semantics.Lexicon;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.syntax.model.SRLFactoredModel.SRLFactoredModelFactory;
import edu.uw.easysrl.syntax.model.SupertagFactoredModel.SupertagFactoredModelFactory;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.parser.AbstractParser.SuperTaggingResults;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.syntax.parser.ParserCKY;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.BackoffSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.syntax.parser.SRLParser.JointSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.SemanticParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.TagDict;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.syntax.training.Training;
import edu.uw.easysrl.util.Util;

public class EasySRL {

	/**
	 * Command Line Interface
	 */
	public interface CommandLineArguments {
		@Option(shortName = "m", description = "Path to the parser model")
		File getModel();

		@Option(shortName = "f", defaultValue = "", description = "(Optional) Path to the input text file. Otherwise, the parser will read from stdin.")
		File getInputFile();

		@Option(shortName = "i", defaultValue = "tokenized", description = "(Optional) Input Format: one of \"tokenized\", \"POStagged\", \"POSandNERtagged\", \"gold\", \"deps\" or \"supertagged\"")
		String getInputFormat();

		@Option(shortName = "o", description = "Output Format: one of \"logic\" \"srl\", \"ccgbank\", \"html\", or \"prolog\"", defaultValue = "logic")
		String getOutputFormat();

		@Option(shortName = "a", description = "Parsing algorithm: one of \"astar\" or \"cky\"", defaultValue = "astar")
		String getParsingAlgorithm();

		@Option(shortName = "l", defaultValue = "100", description = "(Optional) Maximum length of sentences in words. Defaults to 70.")
		int getMaxLength();

		@Option(shortName = "n", defaultValue = "1", description = "(Optional) Number of parses to return per sentence. Defaults to 1.")
		int getNbest();

		@Option(shortName = "r", defaultValue = { "S[dcl]", "S[wq]", "S[q]", "S[qem]", "NP", "S[b]\\NP"// , "S[b]"
		}, description = "(Optional) List of valid categories for the root node of the parse. Defaults to: S[dcl] S[wq] S[q] NP")
		List<Category> getRootCategories();

		@Option(shortName = "s", description = "(Optional) Allow rules not involving category combinations seen in CCGBank. Slows things down by around 20%.")
		boolean getUnrestrictedRules();

		@Option(defaultValue = "0.0001", description = "(Optional) Prunes lexical categories whose probability is less than this ratio of the best category. Defaults to 0.0001.")
		double getSupertaggerbeam();

		@Option(defaultValue = "50", description = "(Optional) Maximum number of categores per word output by the supertagger. Defaults to 50.")
		int getMaxTagsPerWord();

		@Option(defaultValue = "0.0", description = "(Optional) If using N-best parsing, filter parses whose probability is lower than this fraction of the probability of the best parse. Defaults to 0.0")
		double getNbestbeam();

		@Option(defaultValue = "1", description = "(Optional) Number of threads to use. If greater than 1, the output order may differ from the input.")
		int getThreads();

		@Option(helpRequest = true, description = "Display this message", shortName = "h")
		boolean getHelp();

		@Option(description = "(Optional) Make a tag dictionary")
		boolean getMakeTagDict();

	}

	// Set of supported InputFormats
	public enum InputFormat {
		TOKENIZED, GOLD, SUPERTAGGED, POSTAGGED, POSANDNERTAGGED
	}

	// Set of supported OutputFormats
	public enum OutputFormat {
		CCGBANK(ParsePrinter.CCGBANK_PRINTER), HTML(ParsePrinter.HTML_PRINTER), SUPERTAGS(ParsePrinter.SUPERTAG_PRINTER), PROLOG(
				ParsePrinter.PROLOG_PRINTER), EXTENDED(ParsePrinter.EXTENDED_CCGBANK_PRINTER), DEPS(
				new ParsePrinter.DependenciesPrinter()), SRL(ParsePrinter.SRL_PRINTER), LOGIC(
								ParsePrinter.LOGIC_PRINTER);

		public final ParsePrinter printer;

		OutputFormat(final ParsePrinter printer) {
			this.printer = printer;
		}
	}

	public static void main(final String[] args) throws IOException, InterruptedException {

		try {

			final CommandLineArguments commandLineOptions = CliFactory.parseArguments(CommandLineArguments.class, args);
			final InputFormat input = InputFormat.valueOf(commandLineOptions.getInputFormat().toUpperCase());

			if (commandLineOptions.getMakeTagDict()) {
				// InputReader reader = InputReader.make(input, new
				// SyntaxTreeNodeFactory(commandLineOptions.getMaxLength(), 0, 0));
				// Map<String, Collection<Category>> tagDict =
				// TagDict.makeDict(reader.readFile(commandLineOptions.getInputFile()));

				final Map<String, Collection<Category>> tagDict = TagDict.makeDictFromParses(CCGBankParseReader
						.loadCorpus(commandLineOptions.getInputFile(), ".*.parsed.gz"));
				TagDict.writeTagDict(tagDict, new File(commandLineOptions.getModel(), "tagdict"));
				System.exit(0);
			}

			if (!commandLineOptions.getModel().exists()) {
				throw new InputMismatchException("Couldn't load model from from: " + commandLineOptions.getModel());
			}

			final String folder = commandLineOptions.getModel().getAbsolutePath();
			final String pipelineFolder = folder + "/pipeline";
			final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));
			System.err.println("Loading model...");
			final PipelineSRLParser pipeline = new PipelineSRLParser(EasySRL.makeParser(pipelineFolder, 0.0001,
					ParsingAlgorithm.ASTAR, 200000, false),
					Util.deserialize(new File(pipelineFolder, "labelClassifier")), posTagger);

			final SRLParser parser2 = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, 0.01,
					ParsingAlgorithm.valueOf(commandLineOptions.getParsingAlgorithm().toUpperCase()),
					commandLineOptions.getParsingAlgorithm().equals("astar") ? 20000 : 400000, true), posTagger),
					pipeline);

			final OutputFormat outputFormat = OutputFormat.valueOf(commandLineOptions.getOutputFormat().toUpperCase());
			final ParsePrinter printer = outputFormat.printer;

			final SRLParser parser;
			if (printer.outputsLogic()) {
				// If we're outputing logic, load a lexicon
				final File lexiconFile = new File(commandLineOptions.getModel(), "lexicon");
				final Lexicon lexicon = lexiconFile.exists() ? new Lexicon(lexiconFile) : new Lexicon();
				parser = new SemanticParser(parser2, lexicon);
			} else {
				parser = parser2;
			}

			final InputReader reader = InputReader.make(InputFormat.valueOf(commandLineOptions.getInputFormat()
					.toUpperCase()));
			if ((outputFormat == OutputFormat.PROLOG || outputFormat == OutputFormat.EXTENDED)
					&& input != InputFormat.POSANDNERTAGGED) {
				throw new Error("Must use \"-i POSandNERtagged\" for this output");
			}

			final boolean readingFromStdin;
			final Iterator<String> inputLines;
			if (commandLineOptions.getInputFile().getName().isEmpty()) {
				// Read from STDIN
				inputLines = new Scanner(System.in, "UTF-8");
				readingFromStdin = true;
			} else {
				// Read from file
				inputLines = Util.readFile(commandLineOptions.getInputFile()).iterator();
				readingFromStdin = false;
			}

			System.err.println("Parsing...");

			final Stopwatch timer = Stopwatch.createStarted();
			final SuperTaggingResults supertaggingResults = new SuperTaggingResults();
			final Results dependencyResults = new Results();
			final ExecutorService executorService = Executors.newFixedThreadPool(commandLineOptions.getThreads());

			final BufferedWriter sysout = new BufferedWriter(new OutputStreamWriter(System.out));

			int id = 0;
			while (inputLines.hasNext()) {
				// Read each sentence, either from STDIN or a parse.
				final String line = inputLines instanceof Scanner ? ((Scanner) inputLines).nextLine().trim()
						: inputLines.next();
				if (!line.isEmpty() && !line.startsWith("#")) {
					id++;
					final int id2 = id;

					// Make a new ExecutorService job for each sentence to parse.
					executorService.execute(new Runnable() {
						@Override
						public void run() {

							final CCGandSRLparse parse = parser.parseTokens(reader.readInput(line).getInputWords());
							String output;

							output = printer.print(parse.getCcgParse(), id2);

							synchronized (printer) {
								try {
									// It's a bit faster to buffer output than use
									// System.out.println() directly.
									sysout.write(output);
									sysout.newLine();

									if (readingFromStdin) {
										sysout.flush();
									}
								} catch (final IOException e) {
									throw new RuntimeException(e);
								}
							}
						}
					});
				}
			}
			executorService.shutdown();
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
			sysout.close();

			final DecimalFormat twoDP = new DecimalFormat("#.##");
			System.err.println("Coverage: "
					+ twoDP.format(100.0 * supertaggingResults.parsedSentences.get()
							/ supertaggingResults.totalSentences.get()) + "%");
			if (supertaggingResults.totalCats.get() > 0) {
				System.err.println("Accuracy: "
						+ twoDP.format(100.0 * supertaggingResults.rightCats.get()
								/ supertaggingResults.totalCats.get()) + "%");
			}

			if (!dependencyResults.isEmpty()) {
				System.out.println("F1=" + dependencyResults.getF1());
			}

			System.err.println("Sentences parsed: " + supertaggingResults.parsedSentences);
			System.err.println("Speed: "
					+ twoDP.format(1000.0 * supertaggingResults.parsedSentences.get()
							/ timer.elapsed(TimeUnit.MILLISECONDS)) + " sentences per second");

		} catch (final ArgumentValidationException e) {
			System.err.println(e.getMessage());
			System.err.println(CliFactory.createCli(CommandLineArguments.class).getHelpMessage());
		}
	}

	public static Parser makeParser(final File modelFolder) throws IOException {
		CommandLineArguments commandLineOptions;
		try {
			// Meh.
			String rootCats = "";
			for (final Category cat : Training.ROOT_CATEGORIES) {
				rootCats = rootCats + cat + " ";
			}

			commandLineOptions = CliFactory.parseArguments(CommandLineArguments.class,
					new String[] { "-m", modelFolder.toString() });
		} catch (final ArgumentValidationException e) {
			throw new RuntimeException(e);
		}
		return makeParser(commandLineOptions,
				commandLineOptions.getParsingAlgorithm().equals("astar") ? 20000 : 400000, true);

	}

	public enum ParsingAlgorithm {
		ASTAR, CKY
	}

	public static Parser makeParser(final String modelFolder, final double supertaggerBeam,
			final ParsingAlgorithm parsingAlgorithm, final int maxChartSize, final boolean joint) throws IOException {
		CommandLineArguments commandLineOptions;
		try {
			commandLineOptions = CliFactory.parseArguments(CommandLineArguments.class, new String[] { "-m",
				modelFolder, "--supertaggerbeam", "" + supertaggerBeam, "-a", parsingAlgorithm.toString() });

		} catch (final ArgumentValidationException e) {
			throw new RuntimeException(e);
		}
		return makeParser(commandLineOptions, maxChartSize, joint);

	}

	private static Parser makeParser(final CommandLineArguments commandLineOptions, final int maxChartSize,
			final boolean joint) throws IOException {
		DependencyStructure.parseMarkedUpFile(new File(commandLineOptions.getModel(), "markedup"));
		final File cutoffsFile = new File(commandLineOptions.getModel(), "cutoffs");
		final CutoffsDictionary cutoffs = cutoffsFile.exists() ? Util.deserialize(cutoffsFile) : null;

		ModelFactory modelFactory;
		final ParsingAlgorithm algorithm = ParsingAlgorithm.valueOf(commandLineOptions.getParsingAlgorithm()
				.toUpperCase());

		if (joint) {
			modelFactory = new SRLFactoredModelFactory(Util.deserialize(new File(commandLineOptions.getModel(),
					"weights")),
					((FeatureSet) Util.deserialize(new File(commandLineOptions.getModel(), "features")))
					.setSupertaggingFeature(new File(commandLineOptions.getModel(), "/pipeline")),
					TaggerEmbeddings.loadCategories(new File(commandLineOptions.getModel(), "categories")), cutoffs,
					Util.deserialize(new File(commandLineOptions.getModel(), "featureToIndex")),
					commandLineOptions.getSupertaggerbeam());

		} else {
			modelFactory = new SupertagFactoredModelFactory(new TaggerEmbeddings(commandLineOptions.getModel(),
					commandLineOptions.getSupertaggerbeam(), commandLineOptions.getMaxTagsPerWord(), cutoffs));

		}

		final Parser parser;
		if (algorithm == ParsingAlgorithm.CKY) {
			parser = new ParserCKY(

			modelFactory, commandLineOptions.getMaxLength(), commandLineOptions.getNbest(),
					commandLineOptions.getNbestbeam(), InputFormat.valueOf(commandLineOptions.getInputFormat()
							.toUpperCase()), Training.ROOT_CATEGORIES, // commandLineOptions.getRootCategories(),
					commandLineOptions.getModel(), maxChartSize);
		} else {
			parser = new ParserAStar(

			modelFactory, commandLineOptions.getMaxLength(), commandLineOptions.getNbest(),
					commandLineOptions.getNbestbeam(), InputFormat.valueOf(commandLineOptions.getInputFormat()
							.toUpperCase()), Training.ROOT_CATEGORIES, // commandLineOptions.getRootCategories(),
					commandLineOptions.getModel(), maxChartSize);
		}

		return parser;
	}

	public static void printDetailedTiming(final Parser parser, final DecimalFormat format) {
		// Prints some statistic about how long each length sentence took to
		// parse.
		int sentencesCovered = 0;
		final Multimap<Integer, Long> sentenceLengthToParseTimeInNanos = parser.getSentenceLengthToParseTimeInNanos();
		int binNumber = 0;
		final int binSize = 10;
		while (sentencesCovered < sentenceLengthToParseTimeInNanos.size()) {
			double totalTimeForBinInMillis = 0;
			int totalSentencesInBin = 0;
			for (int sentenceLength = binNumber * binSize + 1; sentenceLength < 1 + (binNumber + 1) * binSize; sentenceLength = sentenceLength + 1) {
				for (final long time : sentenceLengthToParseTimeInNanos.get(sentenceLength)) {
					totalTimeForBinInMillis += ((double) time / 1000000);
					totalSentencesInBin++;
				}
			}
			sentencesCovered += totalSentencesInBin;
			final double averageTimeInMillis = totalTimeForBinInMillis / (totalSentencesInBin);
			if (totalSentencesInBin > 0) {
				System.err.println("Average time for sentences of length " + (1 + binNumber * binSize) + "-"
						+ (binNumber + 1) * binSize + " (" + totalSentencesInBin + "): "
						+ format.format(averageTimeInMillis) + "ms");
			}

			binNumber++;
		}

		final int totalSentencesTimes1000 = sentenceLengthToParseTimeInNanos.size() * 1000;
		final long totalMillis = parser.getParsingTimeOnlyInMillis() + parser.getTaggingTimeOnlyInMillis();
		System.err.println("Just Parsing Time: " + parser.getParsingTimeOnlyInMillis() + "ms "
				+ (totalSentencesTimes1000 / parser.getParsingTimeOnlyInMillis()) + " per second");
		System.err.println("Just Tagging Time: " + parser.getTaggingTimeOnlyInMillis() + "ms "
				+ (totalSentencesTimes1000 / parser.getTaggingTimeOnlyInMillis()) + " per second");
		System.err.println("Total Time:        " + totalMillis + "ms " + (totalSentencesTimes1000 / totalMillis)
				+ " per second");
	}
}
