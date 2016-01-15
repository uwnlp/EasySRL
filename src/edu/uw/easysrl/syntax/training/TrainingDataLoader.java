package edu.uw.easysrl.syntax.training;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AtomicDouble;

import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.Tagger.ScoredCategory;
import edu.uw.easysrl.syntax.training.CKY.ChartCell;
import edu.uw.easysrl.syntax.training.Optimization.TrainingExample;

/**
 * Builds the charts used for discriminative training.
 *
 */
class TrainingDataLoader {

	private final CutoffsDictionaryInterface cutoffsDictionary;
	private final TrainingDataParameters dataParameters;
	private final Multimap<Category, UnaryRule> unaryRules;
	private final CKY parser;
	private final Tagger tagger;
	private final boolean backoff;
	private final POSTagger posTagger;

	TrainingDataLoader(final CutoffsDictionaryInterface cutoffsDictionary, final TrainingDataParameters dataParameters,
			final boolean backoff) {
		super();
		this.cutoffsDictionary = cutoffsDictionary;
		this.dataParameters = dataParameters;
		this.backoff = backoff;

		try {
			unaryRules = AbstractParser.loadUnaryRules(new File(this.dataParameters.getExistingModel(), "unaryRules"));

			Coindexation.parseMarkedUpFile(new File(dataParameters.getExistingModel(), "markedup"));

			// Build set of possible parses
			this.parser = new CKY(dataParameters.getExistingModel(), dataParameters.maxTrainingSentenceLength,
					dataParameters.maxChartSize);
			this.tagger = Tagger.make(dataParameters.getExistingModel(), dataParameters.supertaggerBeam, 50,
					cutoffsDictionary// null
					);
			this.posTagger = POSTagger.getStanfordTagger(new File(dataParameters.getExistingModel(), "posTagger"));

		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	List<TrainingExample> makeTrainingData(final Iterator<Sentence> sentences, final boolean singleThread)
			throws IOException {

		return buildTrainingData(sentences, singleThread);

	}

	private List<TrainingExample> buildTrainingData(final Iterator<Sentence> sentences, final boolean singleThread)
			throws IOException {
		// Iterate over training sentences
		final ExecutorService executor = Executors.newFixedThreadPool(singleThread ? 1 : Runtime.getRuntime()
				.availableProcessors());

		final List<TrainingExample> result = new ArrayList<>();

		while (sentences.hasNext()) {
			final Sentence sentence = sentences.next();

			executor.submit(new Runnable() {
				@Override
				public void run() {
					try {
						final TrainingExample trainingExample = makeTrainingExample(sentence);
						if (trainingExample != null) {
							synchronized (result) {
								result.add(trainingExample);
							}
						}

					} catch (final Throwable t) {
						t.printStackTrace();
					}

				}
			});

		}

		executor.shutdown(); // always reclaim resources
		try {
			executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
		} catch (final InterruptedException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

	/**
	 * Creates a training example for a sentence, containing a complete chart of all parses, and a subset used as
	 * positive training examples
	 */
	private TrainingExample makeTrainingExample(final Sentence sentence) {

		if (sentence.getLength() > dataParameters.maxTrainingSentenceLength) {
			return null;
		}

		try {

			// Build a complete chart for the training sentence.
			final AtomicDouble beta = new AtomicDouble(dataParameters.supertaggerBeam);
			final CompressedChart completeChart = parseSentence(sentence.getWords(), beta, Training.ROOT_CATEGORIES);

			if (completeChart == null) {
				// Unable to parse sentence
				return null;
			}

			// Build a smaller chart, which will be used for identifying
			// positive examples.
			final CompressedChart smallChart = parseSentence(
					sentence.getWords(),
					// Make sure the value of the beam is at least the value
					// used for parsing the training charts. Otherwise, the
					// positive chart can be a superset of the complete chart.
					new AtomicDouble(Math.max(dataParameters.supertaggerBeamForGoldCharts, beta.doubleValue())),
					Training.ROOT_CATEGORIES);
			if (smallChart == null) {
				// Unable to parse sentence with restrictive supertagger beam.
				// TODO I guess we could try backing off here.

				final StringBuilder message = new StringBuilder();
				for (final String word : sentence.getWords()) {
					message.append(word + " ");
				}
				message.append("\n");
				for (final Category supertag : sentence.getLexicalCategories()) {
					message.append(supertag + " ");
				}
				// System.err.println(message.toString());

				return null;
			}

			// Now find the parses which are maximally consistent with the SRL.
			final CompressedChart goldChart = new GoldChartFinder(smallChart, dataParameters.usingCCGbankDependencies)
			.goldChart(sentence, cutoffsDictionary);

			if (goldChart == null) {
				// No matched dependencies, so we can't learn against this
				// chart.
				return null;
			}

			final TrainingExample ex = new TrainingExample(completeChart, goldChart, posTagger.tag(sentence
					.getInputWords()), cutoffsDictionary);

			return ex;
		} catch (final Exception e) {
			e.printStackTrace();
			return null;
		}

	}

	static class TrainingDataParameters implements Serializable {
		public boolean usingCCGbankDependencies;
		private final double supertaggerBeamForGoldCharts;
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final int maxChartSize;
		private final double supertaggerBeam;
		private final int maxTrainingSentenceLength;
		private final Collection<Category> possibleRootCategories;
		private final File existingModel;

		TrainingDataParameters(final double supertaggerBeam, final int maxTrainingSentenceLength,
				final Collection<Category> possibleRootCategories, final File existingModel, final int maxChartSize,
				final double supertaggerBeamForGoldCharts, final boolean usingCCGbankDependencies) {
			super();
			this.supertaggerBeam = supertaggerBeam;
			this.maxTrainingSentenceLength = maxTrainingSentenceLength;
			this.possibleRootCategories = possibleRootCategories;
			this.existingModel = existingModel;
			this.supertaggerBeamForGoldCharts = supertaggerBeamForGoldCharts;

			this.maxChartSize = maxChartSize;
			this.usingCCGbankDependencies = usingCCGbankDependencies;

			try {
				this.unaryRules = AbstractParser.loadUnaryRules(new File(existingModel, "unaryRules"));
			} catch (final IOException e) {
				throw new RuntimeException();
			}

		}

		private final Multimap<Category, UnaryRule> unaryRules;

		public Multimap<Category, UnaryRule> getUnaryRules() {

			return unaryRules;
		}

		public File getExistingModel() {
			return existingModel;
		}

		public Collection<Category> getPossibleRootCategories() {
			return possibleRootCategories;
		}

	}

	/**
	 * Build a chart for the sentence using the specified supertagger beam. If the chart exceeds the maximum size, beta
	 * is doubled and the parser will re-try. When the function returns, beta will contain the value of the beam used
	 * for the returned chart.
	 *
	 */
	CompressedChart parseSentence(final List<String> sentence, final AtomicDouble beta,
			final Collection<Category> rootCategories) {
		final CompressedChart compressed;

		final List<Collection<Category>> categories = new ArrayList<>();
		final List<List<ScoredCategory>> tagsForSentence = tagger.tag(InputWord.listOf(sentence));
		for (final List<ScoredCategory> tagsForWord : tagsForSentence) {
			final List<Category> tagsForWord2 = new ArrayList<>();

			final double threshold = beta.doubleValue() * Math.exp(tagsForWord.get(0).getScore());

			for (final ScoredCategory leaf : tagsForWord) {
				if (Math.exp(leaf.getScore()) < threshold) {
					break;
				}
				tagsForWord2.add(leaf.getCategory());
			}

			categories.add(tagsForWord2);
		}

		// Find set of all parses
		final ChartCell[][] chart = parser.parse(sentence, categories);

		if (chart == null) {
			if (beta.doubleValue() * 2 < 0.1 && backoff) {
				beta.set(beta.doubleValue() * 2);
				return parseSentence(sentence, beta, rootCategories);
			} else {
				return null;
			}
		}
		if (chart[0][chart.length - 1] == null || chart[0][chart.length - 1].getEntries().size() == 0) {
			return null;
		}

		compressed = CompressedChart.make(InputWord.listOf(sentence), chart, cutoffsDictionary, unaryRules,
				rootCategories);

		return compressed;
	}
}
