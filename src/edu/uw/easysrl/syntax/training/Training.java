package edu.uw.easysrl.syntax.training;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.StandardCopyOption;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import lbfgsb.DifferentiableFunction;

import com.google.common.base.Objects;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.syntax.evaluation.SRLEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.CutoffsDictionary;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.syntax.model.feature.ArgumentSlotFeature;
import edu.uw.easysrl.syntax.model.feature.BilexicalFeature;
import edu.uw.easysrl.syntax.model.feature.Clustering;
import edu.uw.easysrl.syntax.model.feature.DenseLexicalFeature;
import edu.uw.easysrl.syntax.model.feature.Feature;
import edu.uw.easysrl.syntax.model.feature.Feature.BinaryFeature;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.model.feature.Feature.RootCategoryFeature;
import edu.uw.easysrl.syntax.model.feature.Feature.UnaryRuleFeature;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.model.feature.PrepositionFeature;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.BackoffSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.JointSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.syntax.tagger.TagDict;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.syntax.training.Optimization.LossFunction;
import edu.uw.easysrl.syntax.training.Optimization.TrainingAlgorithm;
import edu.uw.easysrl.syntax.training.Optimization.TrainingExample;
import edu.uw.easysrl.syntax.training.TrainingDataLoader.TrainingDataParameters;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Logger;

public class Training {

	public static final List<Category> ROOT_CATEGORIES = Arrays.asList(Category.valueOf("S[dcl]"),
			Category.valueOf("S[q]"), Category.valueOf("S[wq]"), Category.valueOf("NP"), Category.valueOf("S[b]\\NP")

	);

	private final Logger trainingLogger;

	public static void main(final String[] args) throws IOException, InterruptedException, NotBoundException {
		if (args.length == 0) {
			System.out.println("Please supply a file containing training settings");
			System.exit(0);
		}

		final File propertiesFile = new File(args[0]);
		final Properties trainingSettings = Util.loadProperties(propertiesFile);

		LocateRegistry.createRegistry(1099);

		final List<Clustering> clusterings = new ArrayList<>();

		// Dummy clustering (i.e. words)
		clusterings.add(null);

		for (final String file : trainingSettings.getProperty("clusterings").split(",")) {
			clusterings.add(new Clustering(new File(file), false));
		}

		final boolean local = args.length == 1;

		for (final int minFeatureCount : parseIntegers(trainingSettings, "minimum_feature_frequency")) {
			for (final int maxChart : parseIntegers(trainingSettings, "max_chart_size")) {
				for (final double sigmaSquared : parseDoubles(trainingSettings, "sigma_squared")) {
					for (final double goldBeam : parseDoubles(trainingSettings, "beta_for_positive_charts")) {
						for (final double costFunctionWeight : parseDoubles(trainingSettings, "cost_function_weight")) {
							for (final double beta : parseDoubles(trainingSettings, "beta_for_training_charts")) {

								for (final boolean trainSupertaggerWeight : Arrays.asList(false, true)) {
									final File modelFolder = new File(trainingSettings.getProperty("output_folder")
											.replaceAll("~", Util.getHomeFolder().getAbsolutePath()));

									modelFolder.mkdirs();
									Files.copy(propertiesFile, new File(modelFolder, "training.properties"));

									// Pre-trained EasyCCG model
									final File baseModel = new File(trainingSettings.getProperty(
											"supertagging_model_folder").replaceAll("~",
													Util.getHomeFolder().getAbsolutePath()));

									if (!baseModel.exists()) {
										throw new IllegalArgumentException("Supertagging model not found: "
												+ baseModel.getAbsolutePath());
									}

									final File pipeline = new File(modelFolder, "pipeline");
									pipeline.mkdir();
									for (final File f : baseModel.listFiles()) {

										java.nio.file.Files.copy(f.toPath(), new File(pipeline, f.getName()).toPath(),
												StandardCopyOption.REPLACE_EXISTING);
									}
									final TrainingDataParameters dataParameters = new TrainingDataParameters(beta, 70,
											ROOT_CATEGORIES, baseModel, maxChart, goldBeam, false);

									// Features to use
									final FeatureSet allFeatures = new FeatureSet(
											new DenseLexicalFeature(pipeline, 0.0),
											BilexicalFeature.getBilexicalFeatures(clusterings, 3),
											ArgumentSlotFeature.argumentSlotFeatures, Feature.unaryRules,
											PrepositionFeature.prepositionFeaures, Collections.emptyList(),
											Collections.emptyList());

									final TrainingParameters standard = new Training.TrainingParameters(50,
											allFeatures, sigmaSquared, minFeatureCount, modelFolder,
											costFunctionWeight, trainSupertaggerWeight);

									final Training training = new Training(dataParameters, standard);

									if (local) {
										training.trainLocal();
									} else {
										training.trainDistributed();
									}

									for (final double beam : parseDoubles(trainingSettings, "beta_for_decoding")) {
										System.out.println(Objects.toStringHelper("Settings").add("DecodingBeam", beam)
												.add("MinFeatureCount", minFeatureCount).add("maxChart", maxChart)
												.add("sigmaSquared", sigmaSquared)
												.add("cost_function_weight", costFunctionWeight)
												.add("beta_for_positive_charts", goldBeam)
												.add("beta_for_training_charts", beta)
												.add("train_supertagger", trainSupertaggerWeight).toString());

										for (final Double supertaggerWeight : Arrays.asList(null, 0.5, 0.6, 0.7, 0.8,
												0.9, 1.0, 1.1, 1.2)) {
											System.out.println("supertaggerWeight=" + supertaggerWeight);
											training.evaluate(beam, supertaggerWeight == null ? Optional.empty()
													: Optional.of(supertaggerWeight));
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	private static List<String> parseStrings(final Properties settings, final String field) {
		return Arrays.asList(settings.getProperty(field).split(";"));
	}

	private static List<Integer> parseIntegers(final Properties settings, final String field) {
		return parseStrings(settings, field).stream().map(x -> Integer.valueOf(x)).collect(Collectors.toList());
	}

	private static List<Double> parseDoubles(final Properties settings, final String field) {
		return parseStrings(settings, field).stream().map(x -> Double.valueOf(x)).collect(Collectors.toList());
	}

	private List<TrainingExample> makeTrainingData(final boolean isDev) throws IOException {
		final boolean singleThread = isDev;
		return new TrainingDataLoader(cutoffsDictionary, dataParameters, true /* backoff */).makeTrainingData(
				ParallelCorpusReader.READER.readCorpus(isDev), singleThread);
	}

	private double[] trainLocal() throws IOException {
		final Set<FeatureKey> boundedFeatures = new HashSet<>();
		final Map<FeatureKey, Integer> featureToIndex = makeKeyToIndexMap(trainingParameters.minimumFeatureFrequency,
				boundedFeatures);

		final List<TrainingExample> data = makeTrainingData(true /* not dev */);// FIXME false

		final LossFunction lossFunction = Optimization.getLossFunction(data, featureToIndex, trainingParameters,
				trainingLogger);

		final double[] weights = train(lossFunction, featureToIndex, boundedFeatures);

		return weights;
	}

	private double[] trainDistributed() throws IOException, NotBoundException {
		final Set<FeatureKey> boundedFeatures = new HashSet<>();
		final Map<FeatureKey, Integer> featureToIndex = makeKeyToIndexMap(trainingParameters.minimumFeatureFrequency,
				boundedFeatures);

		final Collection<RemoteTrainer> workers = getTrainers(featureToIndex);

		System.out.println("Training nodes: " + workers.size());

		final List<Runnable> tasks = new ArrayList<>();

		final int sentencesToLoad = Iterators.size(ParallelCorpusReader.READER.readCorpus(false /* not dev */));
		final int shardSize = sentencesToLoad / workers.size();

		int i = 0;
		for (final RemoteTrainer worker : workers) {
			final int start = i * shardSize;
			final int end = start + shardSize;
			tasks.add(new Runnable() {

				@Override
				public void run() {
					try {
						worker.loadData(start, end, dataParameters, trainingParameters.getModelFolder(), trainingLogger);
					} catch (final Throwable e) {
						throw new RuntimeException(e);
					}

				}
			});
			i++;

		}

		Util.runJobsInParallel(tasks, workers.size());

		final double[] weights = train(new DistributedLossFunction(workers, trainingParameters.getSigmaSquared()),
				featureToIndex, boundedFeatures);

		return weights;
	}

	private Collection<RemoteTrainer> getTrainers(final Map<FeatureKey, Integer> featureToIndex)
			throws NotBoundException, IOException {
		final File modelFolder = trainingParameters.getModelFolder();

		modelFolder.mkdirs();
		// Much quicker to transfer settings with files than over RMI
		Util.serialize(trainingParameters, new File(modelFolder, "parameters"));
		Util.serialize(cutoffsDictionary, new File(modelFolder, "cutoffs"));
		Util.serialize(featureToIndex, trainingParameters.getFeatureToIndexFile());

		final List<String> servers = new ArrayList<>();
		for (final String line : Util.readFile(new File("servers.txt"))) {
			servers.add(line);
		}

		return RemoteTrainer.getTrainers(servers);
	}

	private double[] train(final DifferentiableFunction lossFunction, final Map<FeatureKey, Integer> featureToIndex,
			final Set<FeatureKey> boundedFeatures) throws IOException {
		trainingParameters.getModelFolder().mkdirs();

		final double[] weights = new double[featureToIndex.size()];

		// Do training
		train(lossFunction, Optimization.makeLBFGS(featureToIndex, boundedFeatures), weights);

		// Save model
		Util.serialize(weights, trainingParameters.getWeightsFile());
		Util.serialize(trainingParameters.getFeatureSet(), trainingParameters.getFeaturesFile());
		Util.serialize(featureToIndex, trainingParameters.getFeatureToIndexFile());

		final File modelFolder = trainingParameters.getModelFolder();
		modelFolder.mkdirs();
		Files.copy(new File(dataParameters.getExistingModel(), "categories"), new File(modelFolder, "categories"));
		Util.serialize(trainingParameters.getFeatureSet(), new File(modelFolder, "features"));
		Util.serialize(cutoffsDictionary, new File(modelFolder, "cutoffs"));
		Files.copy(new File(dataParameters.getExistingModel(), "unaryRules"), new File(modelFolder, "unaryRules"));
		Files.copy(new File(dataParameters.getExistingModel(), "markedup"), new File(modelFolder, "markedup"));
		Files.copy(new File(dataParameters.getExistingModel(), "seenRules"), new File(modelFolder, "seenRules"));

		return weights;
	}

	/**
	 * Creates a map from (sufficiently frequent) features to integers
	 */
	private Map<FeatureKey, Integer> makeKeyToIndexMap(final int minimumFeatureFrequency,
			final Set<FeatureKey> boundedFeatures) throws IOException {
		final Multiset<FeatureKey> keyCount = HashMultiset.create();
		final Multiset<FeatureKey> bilexicalKeyCount = HashMultiset.create();
		final Map<FeatureKey, Integer> result = new HashMap<>();
		final Multiset<FeatureKey> binaryFeatureCount = HashMultiset.create();
		final Iterator<Sentence> sentenceIt = ParallelCorpusReader.READER.readCorpus(false);
		while (sentenceIt.hasNext()) {
			final Sentence sentence = sentenceIt.next();
			final List<ResolvedDependency> goldDeps = getGoldDeps(sentence);

			final List<Category> cats = sentence.getLexicalCategories();

			for (int i = 0; i < cats.size(); i++) {

				final FeatureKey key = trainingParameters.getFeatureSet().lexicalCategoryFeatures.getFeatureKey(
						sentence.getInputWords(), i, cats.get(i));
				if (key != null) {
					keyCount.add(key);
				}

			}

			for (final ResolvedDependency dep : goldDeps) {
				final SRLLabel role = dep.getSemanticRole();
				// if (cutoffsDictionary.isFrequentWithAnySRLLabel(
				// dep.getCategory(), dep.getArgNumber())
				// && cutoffsDictionary.isFrequent(dep.getCategory(),
				// dep.getArgNumber(), dep.getSemanticRole())) {
				for (final ArgumentSlotFeature feature : trainingParameters.getFeatureSet().argumentSlotFeatures) {
					final FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getHead(), role,
							dep.getCategory(), dep.getArgNumber(), dep.getPreposition());
					keyCount.add(key);
				}

				if (dep.getPreposition() != Preposition.NONE) {
					for (final PrepositionFeature feature : trainingParameters.getFeatureSet().prepositionFeatures) {
						final FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getHead(),
								dep.getCategory(), dep.getPreposition(), dep.getArgNumber());
						keyCount.add(key);
					}
				}
				// }

				if (dep.getSemanticRole() != SRLFrame.NONE) {
					for (final BilexicalFeature feature : trainingParameters.getFeatureSet().dependencyFeatures) {
						final FeatureKey key = feature.getFeatureKey(sentence.getInputWords(), dep.getSemanticRole(),
								dep.getHead(), dep.getArgumentIndex());
						bilexicalKeyCount.add(key);
					}
				}

			}
			if (!trainingParameters.getTrainSupertaggerWeight()) {
				boundedFeatures.add(trainingParameters.getFeatureSet().lexicalCategoryFeatures.getDefault());
			}

			getFromDerivation(sentence.getCcgbankParse(), binaryFeatureCount, boundedFeatures,
					sentence.getInputWords(), 0, sentence.getInputWords().size());

			for (final RootCategoryFeature rootFeature : trainingParameters.getFeatureSet().rootFeatures) {
				final FeatureKey key = rootFeature.getFeatureKey(sentence.getCcgbankParse().getCategory(),
						sentence.getInputWords());
				boundedFeatures.add(key);
				keyCount.add(key);
			}

		}

		result.put(trainingParameters.getFeatureSet().lexicalCategoryFeatures.getDefault(), result.size());

		addFrequentFeatures(minimumFeatureFrequency, binaryFeatureCount, result, boundedFeatures, true);
		addFrequentFeatures(minimumFeatureFrequency, keyCount, result, boundedFeatures, false);
		addFrequentFeatures(minimumFeatureFrequency, bilexicalKeyCount, result, boundedFeatures, false);

		for (final BinaryFeature feature : trainingParameters.getFeatureSet().binaryFeatures) {
			boundedFeatures.add(feature.getDefault());
		}

		for (final Feature feature : trainingParameters.getFeatureSet().getAllFeatures()) {
			if (!result.containsKey(feature.getDefault())) {
				result.put(feature.getDefault(), result.size());
			}
			feature.resetDefaultIndex();
		}

		System.out.println("Total features: " + result.size());
		return result;
	}

	private void getFromDerivation(final SyntaxTreeNode node, final Multiset<FeatureKey> binaryFeatureCount,
			final Set<FeatureKey> boundedFeatures, final List<InputWord> words, final int startIndex, final int endIndex) {
		if (node.getChildren().size() == 2) {
			final SyntaxTreeNode left = node.getChild(0);
			final SyntaxTreeNode right = node.getChild(1);

			for (final RuleProduction rule : Combinator.getRules(left.getCategory(), right.getCategory(),
					Combinator.STANDARD_COMBINATORS)) {
				if (rule.getCategory().equals(node.getCategory())) {
					for (final BinaryFeature feature : trainingParameters.featureSet.binaryFeatures) {
						final FeatureKey featureKey = feature.getFeatureKey(node.getCategory(), node.getRuleType(),
								left.getCategory(), left.getRuleType().getNormalFormClassForRule(), 0,
								right.getCategory(), right.getRuleType().getNormalFormClassForRule(), 0, null);
						binaryFeatureCount.add(featureKey);
					}
				}
			}
		}

		if (node.getChildren().size() == 1) {
			for (final UnaryRule rule : dataParameters.getUnaryRules().values()) {
				for (final UnaryRuleFeature feature : trainingParameters.featureSet.unaryRuleFeatures) {
					final FeatureKey key = feature.getFeatureKey(rule.getID(), words, startIndex, endIndex);
					binaryFeatureCount.add(key);
				}

			}

			Util.debugHook();
		}

		int start = startIndex;

		for (final SyntaxTreeNode child : node.getChildren()) {
			final int end = start + child.getLength();
			getFromDerivation(child, binaryFeatureCount, boundedFeatures, words, start, end);
			start = end;
		}
	}

	private void addFrequentFeatures(final int minCount, final Multiset<FeatureKey> keyCount,
			final Map<FeatureKey, Integer> result, final Set<FeatureKey> boundedFeatures, final boolean upperBound0) {
		for (final com.google.common.collect.Multiset.Entry<FeatureKey> entry : keyCount.entrySet()) {
			if (entry.getCount() >= minCount) {
				result.put(entry.getElement(), result.size());

				if (upperBound0) {
					boundedFeatures.add(entry.getElement());
				}

			}
		}
	}

	/**
	 * Used for identifying features that occur in positive examples.
	 *
	 * Compares labels gold-standard CCGbank dependencies with SRL labels.
	 */
	static List<ResolvedDependency> getGoldDeps(final Sentence sentence) {
		final List<Category> goldCategories = sentence.getLexicalCategories();
		final List<ResolvedDependency> goldDeps = new ArrayList<>();
		final Set<CCGBankDependency> unlabelledDeps = new HashSet<>(sentence.getCCGBankDependencyParse()
				.getDependencies());
		for (final Entry<SRLDependency, CCGBankDependency> dep : sentence.getCorrespondingCCGBankDependencies()
				.entrySet()) {
			final CCGBankDependency ccgbankDep = dep.getValue();
			if (ccgbankDep == null) {
				continue;
			}

			final Category goldCategory = goldCategories.get(ccgbankDep.getSentencePositionOfPredicate());
			if (ccgbankDep.getArgNumber() > goldCategory.getNumberOfArguments()) {
				// SRL_rebank categories are out of sync with Rebank deps
				continue;
			}

			goldDeps.add(new ResolvedDependency(ccgbankDep.getSentencePositionOfPredicate(), goldCategory, ccgbankDep
					.getArgNumber(), ccgbankDep.getSentencePositionOfArgument(), dep.getKey().getLabel(), Preposition
					.fromString(dep.getKey().getPreposition())));
			unlabelledDeps.remove(ccgbankDep);
		}

		for (final CCGBankDependency dep : unlabelledDeps) {
			final Category goldCategory = goldCategories.get(dep.getSentencePositionOfPredicate());
			if (dep.getArgNumber() > goldCategory.getNumberOfArguments()) {
				// SRL_rebank categories are out of sync with Rebank deps
				continue;
			}

			final Preposition preposition = Preposition.NONE;

			goldDeps.add(new ResolvedDependency(dep.getSentencePositionOfPredicate(), goldCategory, dep.getArgNumber(),
					dep.getSentencePositionOfArgument(), SRLFrame.NONE, preposition));
		}

		return goldDeps;
	}

	private void train(final DifferentiableFunction lossFunction, final TrainingAlgorithm algorithm,
			final double[] weights) throws IOException {
		trainingLogger.log("Starting Training");
		algorithm.train(lossFunction, weights);
		trainingLogger.log("Training Completed");
	}

	private final TrainingDataParameters dataParameters;
	private final Training.TrainingParameters trainingParameters;
	private final CutoffsDictionaryInterface cutoffsDictionary;

	private Training(final TrainingDataParameters dataParameters, final Training.TrainingParameters parameters)
			throws IOException {
		super();
		this.dataParameters = dataParameters;
		this.trainingParameters = parameters;
		this.trainingLogger = new Logger(trainingParameters.getLogFile());

		final List<Category> lexicalCategoriesList = TaggerEmbeddings.loadCategories(new File(dataParameters
				.getExistingModel(), "categories"));
		this.cutoffsDictionary = new CutoffsDictionary(lexicalCategoriesList, TagDict.readDict(
				dataParameters.getExistingModel(), new HashSet<>(lexicalCategoriesList)),
				trainingParameters.maxDependencyLength);

	}

	private void evaluate(final double testingSupertaggerBeam, final Optional<Double> supertaggerWeight)
			throws IOException {
		final int maxSentenceLength = 70;
		final POSTagger posTagger = POSTagger
				.getStanfordTagger(new File(dataParameters.getExistingModel(), "posTagger"));

		final SRLParser parser = new JointSRLParser(EasySRL.makeParser(trainingParameters.getModelFolder()
				.getAbsolutePath(), testingSupertaggerBeam, ParsingAlgorithm.ASTAR, 20000, true, supertaggerWeight, 1,
				70), posTagger);

		final SRLParser backoff = new BackoffSRLParser(parser, new PipelineSRLParser(EasySRL.makeParser(dataParameters
				.getExistingModel().getAbsolutePath(), 0.0001, ParsingAlgorithm.ASTAR, 100000, false, Optional.empty(),
				1, 70), Util.deserialize(new File(dataParameters.getExistingModel(), "labelClassifier")), posTagger));

		final Results results = SRLEvaluation
				.evaluate(backoff, ParallelCorpusReader.getPropBank00(), maxSentenceLength);

		System.out.println("Final result: F1=" + results.getF1());

	}

	static class TrainingParameters implements Serializable {
		final int minimumFeatureFrequency;
		/**
		 *
		 */
		private static final long serialVersionUID = 6752386432642051238L;
		private final FeatureSet featureSet;
		private final double sigmaSquared;
		private final int maxDependencyLength;
		private final File modelFolder;
		private final double costFunctionWeight;
		private final boolean trainSupertaggerWeight;

		TrainingParameters(final int maxDependencyLength, final FeatureSet featureSet, final double sigmaSquared,
				final int minimumFeatureFrequency, final File modelFolder, final double costFunctionWeight,
				final boolean trainSupertaggerWeight) {
			super();
			this.featureSet = featureSet;
			this.sigmaSquared = sigmaSquared;
			this.maxDependencyLength = maxDependencyLength;
			this.minimumFeatureFrequency = minimumFeatureFrequency;
			this.modelFolder = modelFolder;
			this.costFunctionWeight = costFunctionWeight;
			this.trainSupertaggerWeight = trainSupertaggerWeight;
		}

		private Object readResolve() {
			// Hack to deal with transient DenseLexicalFeature
			try {
				return new TrainingParameters(maxDependencyLength, featureSet.setSupertaggingFeature(new File(
						modelFolder, "pipeline"), 0.0), sigmaSquared, minimumFeatureFrequency, modelFolder,
						costFunctionWeight, trainSupertaggerWeight);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		public DenseLexicalFeature getLexicalCategoryFeatures() {
			return featureSet.lexicalCategoryFeatures;
		}

		File getLogFile() {
			return new File(getModelFolder(), "log");
		}

		File getModelFolder() {
			return modelFolder;
		}

		File getFeaturesFile() {
			return new File(getModelFolder(), "features");
		}

		File getWeightsFile() {
			return new File(getModelFolder(), "weights");
		}

		File getFeatureToIndexFile() {
			return new File(getModelFolder(), "featureToIndex");
		}

		int getMaxDependencyLength() {
			return maxDependencyLength;
		}

		FeatureSet getFeatureSet() {
			return featureSet;
		}

		public Collection<ArgumentSlotFeature> getArgumentslotfeatures() {
			return featureSet.argumentSlotFeatures;
		}

		public Collection<BilexicalFeature> getDependencyFeatures() {
			return featureSet.dependencyFeatures;

		}

		public double getSigmaSquared() {
			return sigmaSquared;
		}

		public double getCostFunctionWeight() {
			return costFunctionWeight;
		}

		public boolean getTrainSupertaggerWeight() {
			return trainSupertaggerWeight;
		}

	}

}
