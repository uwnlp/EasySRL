package edu.uw.easysrl.syntax.training;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import lbfgsb.DifferentiableFunction;
import lbfgsb.FunctionValues;
import lbfgsb.IterationFinishedListener;
import lbfgsb.LBFGSBException;
import lbfgsb.Minimizer;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;

import edu.uw.easysrl.syntax.training.ClassifierTrainer.AbstractFeature;
import edu.uw.easysrl.syntax.training.ClassifierTrainer.AbstractTrainingExample;
import edu.uw.easysrl.util.Util;

/**
 * General-purpose log-linear classifier
 *
 */
public abstract class ClassifierTrainer<T extends AbstractTrainingExample<L>, F extends AbstractFeature<T, L>, L> {

	private static class FeatureKey implements Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final List<Object> values;
		private final int hashCode;

		private FeatureKey(final List<Object> values) {
			this.values = values;
			this.hashCode = values.hashCode();
		}

		@Override
		public boolean equals(final Object other) {
			return hashCode() == other.hashCode() && values.equals(((FeatureKey) other).values);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public String toString() {
			return values.toString();
		}
	}

	public static abstract class AbstractTrainingExample<L> {

		public abstract Collection<L> getPossibleLabels();

		public abstract L getLabel();

	}

	private static class CachedTrainingExample<L> {
		private final Map<L, int[]> labelToFeatures;
		private final L label;
		private final String asString;

		public CachedTrainingExample(final Map<L, int[]> labelToFeatures, final L label, final String asString) {
			super();
			this.labelToFeatures = ImmutableMap.copyOf(labelToFeatures);
			this.label = label;
			this.asString = asString;
		}

		@Override
		public String toString() {
			return asString;
		}

	}

	public static class AbstractClassifier<T extends AbstractTrainingExample<L>, F extends AbstractFeature<T, L>, L>
	implements Serializable {
		private static final long serialVersionUID = 1L;

		private final double[] weights;
		private final Collection<F> features;
		private final Map<FeatureKey, Integer> featureToIndex;

		public AbstractClassifier(final double[] weights, final Collection<F> features,
				final Map<FeatureKey, Integer> featureToIndex) {
			super();
			this.weights = weights;
			this.features = features;
			this.featureToIndex = featureToIndex;
		}

		public L classify(final T ex) {

			double bestScore = Double.NEGATIVE_INFINITY;
			L bestLabel = null;

			final Collection<L> frames = ex.getPossibleLabels();

			for (final L label : frames) {
				double score = 0.0;
				for (final F feature : features) {
					score += weights[feature.getIndex(ex, featureToIndex, label)];
				}

				if (score > bestScore) {
					bestLabel = label;
					bestScore = score;
				}
			}

			return bestLabel;
		}

		public double probability(final T ex, final L label) {

			double labelScore = 0.0;
			double totalScore = 0.0;

			final Collection<L> frames = ex.getPossibleLabels();

			for (final L other : frames) {
				double score = 0.0;
				for (final F feature : features) {
					score += weights[feature.getIndex(ex, featureToIndex, other)];
				}

				score = Math.exp(score);
				if (label == other) {
					labelScore = score;
				}

				totalScore += score;
			}

			return labelScore / totalScore;
		}

		private L classify(final CachedTrainingExample<L> ex) {

			double bestScore = Double.NEGATIVE_INFINITY;
			L bestLabel = null;

			for (final Entry<L, int[]> labelToFeatures : ex.labelToFeatures.entrySet()) {
				double score = 0.0;
				for (final int index : labelToFeatures.getValue()) {
					score += weights[index];
				}

				if (score > bestScore) {
					bestLabel = labelToFeatures.getKey();
					bestScore = score;
				}
			}

			return bestLabel;
		}
	}

	public abstract Collection<F> getFeatures();

	private Map<FeatureKey, Integer> makeKeyToIndexMap(final List<T> data, final Collection<F> features,
			final int minFeatureCount) {
		final Multiset<FeatureKey> keyCount = HashMultiset.create();
		final Map<FeatureKey, Integer> result = new HashMap<>();
		for (final T ex : data) {

			for (final F feature : features) {
				final FeatureKey key = feature.getFeatureKey(ex, ex.getLabel());
				keyCount.add(key);
			}

		}

		for (final com.google.common.collect.Multiset.Entry<FeatureKey> entry : keyCount.entrySet()) {
			if (entry.getCount() >= minFeatureCount) {
				result.put(entry.getElement(), result.size());
			}
		}

		for (final F feature : features) {
			result.put(feature.getDefault(), result.size());
		}

		return result;
		// return ImmutableMap.copyOf(result);
	}

	public AbstractClassifier<T, F, L> train(final int minFeatureCount, final double sigmaSquared) throws IOException,
	LBFGSBException {
		final Collection<F> features = getFeatures();
		final List<T> devData = getTrainingData(true);
		final List<T> trainingData = getTrainingData(false);
		final Map<FeatureKey, Integer> featureToIndex = makeKeyToIndexMap(trainingData, features, minFeatureCount);
		final double[] weights = train(features, cache(trainingData, featureToIndex, features),
				cache(devData, featureToIndex, features), sigmaSquared, featureToIndex);

		final AbstractClassifier<T, F, L> classifier = new AbstractClassifier<>(weights, features, featureToIndex);

		return classifier;

	}

	private List<CachedTrainingExample<L>> cache(final List<T> trainingData,
			final Map<FeatureKey, Integer> featureToIndex, final Collection<F> features) {
		final List<CachedTrainingExample<L>> result = new ArrayList<>();
		for (final T ex : trainingData) {
			final Map<L, int[]> labelToFeatures = new HashMap<>();
			for (final L label : ex.getPossibleLabels()) {
				final int[] indices = new int[features.size()];
				int i = 0;
				for (final F feature : features) {
					indices[i] = feature.getIndex(ex, featureToIndex, label);
					i++;
				}
				labelToFeatures.put(label, indices);
			}
			result.add(new CachedTrainingExample<>(labelToFeatures, ex.getLabel(), ex.toString()));
		}
		return result;
	}

	private double[] train(final Collection<F> features, final List<CachedTrainingExample<L>> data,
			final Collection<CachedTrainingExample<L>> dev, final double sigmaSquared,
			final Map<FeatureKey, Integer> featureToIndex) throws LBFGSBException {
		final int numWeights = featureToIndex.size() + 1;
		final double[] weights = new double[numWeights];

		final Minimizer alg = new Minimizer();
		alg.setDebugLevel(5);

		final double[] bestWeights = new double[numWeights];

		alg.getStopConditions().setMaxIterationsInactive();
		alg.getStopConditions().setMaxIterations(250);
		final IterationFinishedListener iterationFinishedListener = new IterationFinishedListener() {
			private int iteration;
			private final double bestScore = Double.NEGATIVE_INFINITY;

			@Override
			public boolean iterationFinished(final double[] newWeights, final double arg1, final double[] arg2) {
				final AbstractClassifier<T, F, L> classifier = new AbstractClassifier<>(newWeights, features,
						featureToIndex);
				int right = 0;

				for (final CachedTrainingExample<L> ex : dev) {
					final L predicted = classifier.classify(ex);
					if (predicted == ex.label) {
						right++;
					} else if (predicted != null) {
						// System.out.println(ex + "\t(" + predicted + ")");
					}
				}
				System.out.println("Iteration: " + iteration++);
				System.out.println("Accuracy = " + Util.twoDP(100.0 * right / dev.size()));

				if (right > bestScore) {
					System.arraycopy(newWeights, 0, bestWeights, 0, newWeights.length);
					;
				}
				return true;
			}
		};
		alg.setIterationFinishedListener(iterationFinishedListener);
		alg.run(new ParallelLossFunction<>(new LossFunction<>(sigmaSquared),
				Runtime.getRuntime().availableProcessors(), data), weights);
		return bestWeights;
	}

	private static class ParallelLossFunction<L> implements DifferentiableFunction {
		private final LossFunction<L> lossFunction;
		private final int numThreads;
		private final List<CachedTrainingExample<L>> trainingData;

		public ParallelLossFunction(final LossFunction<L> lossFunction, final int numThreads,
				final List<CachedTrainingExample<L>> trainingData) {
			super();
			this.lossFunction = lossFunction;
			this.numThreads = numThreads;
			this.trainingData = trainingData;
		}

		@Override
		public IterationResult getValues(final double[] featureWeights) {
			final Collection<Callable<IterationResult>> tasks = new ArrayList<>();

			int totalCorrect = 0;
			final int batchSize = trainingData.size() / numThreads;
			for (final List<CachedTrainingExample<L>> batch : Lists.partition(trainingData, batchSize)) {
				tasks.add(new Callable<IterationResult>() {
					@Override
					public IterationResult call() throws Exception {
						return lossFunction.getValues(featureWeights, batch);
					}
				});
			}

			final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
			List<Future<IterationResult>> results;
			try {
				results = executor.invokeAll(tasks);

				// FunctionValues total = new FunctionValues(0.0, new
				// double[featureWeights.length]);

				final double[] totalGradient = new double[featureWeights.length];
				double totalLoss = 0.0;

				for (final Future<IterationResult> result : results) {
					final IterationResult values = result.get();
					totalLoss += values.functionValue;
					Util.add(totalGradient, values.gradient);
					totalCorrect += values.correctPredictions;
				}
				executor.shutdown(); // always reclaim resources

				System.out.println();

				System.out.println("Training accuracy=" + Util.twoDP(100.0 * totalCorrect / trainingData.size()));
				System.out.println("Loss=" + Util.twoDP(totalLoss));
				return new IterationResult(totalCorrect, totalLoss, totalGradient);

			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static class IterationResult extends FunctionValues {
		public IterationResult(final int correctPredictions, final double functionValue, final double[] gradient) {
			super(functionValue, gradient);
			this.correctPredictions = correctPredictions;
		}

		private final int correctPredictions;

	}

	private static class LossFunction<L> {
		private final double sigmaSquared;

		// private final Multimap<String, SRLFrame> lemmaToSenses;

		private LossFunction(final double sigmaSquared) {
			super();
			this.sigmaSquared = sigmaSquared;
		}

		public IterationResult getValues(final double[] featureWeights,
				final Collection<CachedTrainingExample<L>> trainingData) {
			final double[] modelExpectation = new double[featureWeights.length];
			final double[] goldExpectation = new double[featureWeights.length];

			double loglikelihood = 0.0;
			final AtomicInteger correct = new AtomicInteger();

			for (final CachedTrainingExample<L> trainingExample : trainingData) {
				loglikelihood = loglikelihood
						+ computeExpectationsForTrainingExample(trainingExample, featureWeights, modelExpectation,
								goldExpectation, correct);
			}
			final double[] gradient = Util.subtract(goldExpectation, modelExpectation);

			for (int i = 0; i < featureWeights.length; i++) {
				loglikelihood = loglikelihood - (Math.pow(featureWeights[i], 2) / (2.0 * sigmaSquared));
				gradient[i] = gradient[i] - (featureWeights[i] / sigmaSquared);
			}

			loglikelihood = -loglikelihood;
			for (int i = 0; i < featureWeights.length; i++) {
				gradient[i] = -gradient[i];
			}

			return new IterationResult(correct.get(), loglikelihood, gradient);
		}

		private double computeExpectationsForTrainingExample(final CachedTrainingExample<L> trainingExample,
				final double[] featureWeights, final double[] modelExpectation, final double[] goldExpectation,
				final AtomicInteger correct) {

			if (!trainingExample.labelToFeatures.containsKey(trainingExample.label)) {
				// Gold label is not possible.
				return 0;
			}

			L bestLabel = null;
			double bestScore = Double.NEGATIVE_INFINITY;

			double total = 0.0;
			final Map<L, Double> labelToScore = new HashMap<>();
			for (final Entry<L, int[]> labelToFeatures : trainingExample.labelToFeatures.entrySet()) {
				double score = 0.0;
				for (final int index : labelToFeatures.getValue()) {
					score += featureWeights[index];
				}

				score = Math.exp(score);
				total += score;
				labelToScore.put(labelToFeatures.getKey(), score);

				if (score > bestScore) {
					bestScore = score;
					bestLabel = labelToFeatures.getKey();
				}
			}

			if (bestLabel.equals(trainingExample.label)) {
				correct.getAndIncrement();
			}

			for (final Entry<L, int[]> labelToFeatures : trainingExample.labelToFeatures.entrySet()) {
				final double pLabel = labelToScore.get(labelToFeatures.getKey()) / total;
				for (final int index : labelToFeatures.getValue()) {
					modelExpectation[index] += pLabel;
				}
			}

			final double loglikelihood = Math.log(labelToScore.get(trainingExample.label) / total);

			for (final int index : trainingExample.labelToFeatures.get(trainingExample.label)) {
				goldExpectation[index] += 1;
			}

			return loglikelihood;
		}

	}

	public static abstract class AbstractFeature<T, L> implements Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final String name;
		private final int id;
		private final static AtomicInteger ids = new AtomicInteger();

		private final FeatureKey defaultKey;

		public FeatureKey getFeatureKey(final T trainingExample, final L label) {
			final List<Object> value = new ArrayList<>();
			getValue(value, trainingExample, label);
			value.add(id);
			final FeatureKey result = new FeatureKey(value);
			return result;
		}

		FeatureKey getDefault() {
			return defaultKey;
		}

		public String getName() {
			return name;
		}

		public int getIndex(final T trainingExample, final Map<FeatureKey, Integer> keyToIndex, final L label) {
			Integer result = keyToIndex.get(getFeatureKey(trainingExample, label));
			if (result == null) {
				result = keyToIndex.get(defaultKey);
			}

			return result;
		}

		public AbstractFeature(final String name) {
			super();
			this.name = name;
			this.id = ids.getAndIncrement();
			this.defaultKey = new FeatureKey(Arrays.asList(id));
		}

		public abstract void getValue(List<Object> result, T trainingExample, L label);
	}

	public abstract List<T> getTrainingData(boolean isDev) throws IOException;

}
