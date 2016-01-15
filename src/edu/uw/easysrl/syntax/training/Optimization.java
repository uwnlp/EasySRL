package edu.uw.easysrl.syntax.training;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lbfgsb.Bound;
import lbfgsb.DifferentiableFunction;
import lbfgsb.FunctionValues;
import lbfgsb.LBFGSBException;
import lbfgsb.Minimizer;
import lbfgsb.Result;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Logger;

public class Optimization {

	private static class InvertedLossFunction extends LossFunction {
		private final LossFunction lossFunction;

		private InvertedLossFunction(final LossFunction lossFunction) {
			super(lossFunction.trainingData, lossFunction.logger);

			this.lossFunction = lossFunction;
		}

		@Override
		public FunctionValues getValues2(final double[] featureWeights, final List<TrainingExample> trainingData) {

			final FunctionValues result = lossFunction.getValues2(featureWeights, trainingData);

			final double[] newGradient = new double[featureWeights.length];
			for (int i = 0; i < newGradient.length; i++) {
				newGradient[i] = -result.gradient[i];
			}
			return new FunctionValues(-result.functionValue, newGradient);
		}
	}

	static class LogLossFunction extends LossFunction {

		private final FeatureSet features;
		private final Map<FeatureKey, Integer> featureToIndexMap;

		private LogLossFunction(final List<TrainingExample> trainingData, final FeatureSet features,
				final Map<FeatureKey, Integer> featureToIndexMap, final Logger logger) {
			super(trainingData, logger);

			this.features = features;
			this.featureToIndexMap = featureToIndexMap;
		}

		@Override
		FunctionValues getValues2(final double[] featureWeights, final List<TrainingExample> trainingData) {
			final double[] modelExpectation = new double[featureWeights.length];
			final double[] goldExpectation = new double[featureWeights.length];

			double loglikelihood = 0.0;
			for (final TrainingExample trainingExample : trainingData) {
				loglikelihood = loglikelihood
						+ computeExpectationsForTrainingExample(trainingExample, featureWeights, modelExpectation,
								goldExpectation);
			}

			final double[] gradient = Util.subtract(goldExpectation, modelExpectation);

			final FunctionValues result = new FunctionValues(loglikelihood, gradient);
			return result;
		}

		private double computeExpectationsForTrainingExample(final TrainingExample trainingExample,
				final double[] featureWeights, final double[] modelExpectation, final double[] goldExpectation) {
			double loglikelihood = 0;
			final InsideOutside insideOutsideModel = new InsideOutside(trainingExample.allParses, features,
					trainingExample.words, featureToIndexMap, featureWeights);

			insideOutsideModel.updateFeatureExpectations(modelExpectation);
			loglikelihood -= insideOutsideModel.getLogNormalizingConstant();

			final InsideOutside insideOutsideGold = new InsideOutside(trainingExample.goldParses, features,
					trainingExample.words, featureToIndexMap, featureWeights);

			insideOutsideGold.updateFeatureExpectations(goldExpectation);
			loglikelihood += insideOutsideGold.getLogNormalizingConstant();

			Preconditions.checkState(loglikelihood < 0.1, "Positive charts have higher score than complete charts");

			return loglikelihood;
		}

	}

	static abstract class LossFunction implements DifferentiableFunction {
		private final List<TrainingExample> trainingData;

		private int iterationNumber;
		private final Logger logger;

		private LossFunction(final List<TrainingExample> trainingData, final Logger logger) {
			super();
			this.trainingData = trainingData;
			this.logger = logger;
		}

		abstract FunctionValues getValues2(double[] featureWeights, List<TrainingExample> trainingData);

		@Override
		public final FunctionValues getValues(final double[] featureWeights) {
			logger.log("Calculating expectations for iteration: " + iterationNumber);

			final FunctionValues result = getValues2(featureWeights, trainingData);
			logger.log("Done. Loss = " + result.functionValue);

			iterationNumber++;
			return result;
		}
	}

	private static class ParallelLossFunction extends LossFunction {
		private final LossFunction lossFunction;
		private final int numThreads;

		private ParallelLossFunction(final LossFunction lossFunction, final int numThreads) {
			super(lossFunction.trainingData, lossFunction.logger);
			this.lossFunction = lossFunction;
			this.numThreads = numThreads;

		}

		@Override
		public FunctionValues getValues2(final double[] featureWeights, final List<TrainingExample> trainingData) {
			final Collection<Callable<FunctionValues>> tasks = new ArrayList<>();

			final int batchSize = trainingData.size() / numThreads;
			for (final List<TrainingExample> batch : Lists.partition(trainingData, batchSize)) {
				tasks.add(new Callable<FunctionValues>() {
					@Override
					public FunctionValues call() throws Exception {
						try {
							return lossFunction.getValues2(featureWeights, batch);
						} catch (final Exception e) {
							e.printStackTrace();
							throw e;
						}
					}
				});
			}

			final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
			List<Future<FunctionValues>> results;
			try {
				results = executor.invokeAll(tasks);

				final double[] totalGradient = new double[featureWeights.length];
				double totalLoss = 0.0;

				for (final Future<FunctionValues> result : results) {
					final FunctionValues values = result.get();
					totalLoss += values.functionValue;
					Util.add(totalGradient, values.gradient);
				}
				executor.shutdown(); // always reclaim resources

				return new FunctionValues(totalLoss, totalGradient);

			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}

	}

	private static class RegularizedLossFunction extends LossFunction {
		private final LossFunction lossFunction;

		private RegularizedLossFunction(final LossFunction lossFunction, final double sigmaSquared) {
			super(lossFunction.trainingData, lossFunction.logger);
			this.sigmaSquared = sigmaSquared;
			this.lossFunction = lossFunction;
		}

		private final double sigmaSquared;

		@Override
		FunctionValues getValues2(final double[] featureWeights, final List<TrainingExample> trainingData) {
			final FunctionValues unregularized = lossFunction.getValues2(featureWeights, trainingData);

			// L2 regularization
			double loglikelihood = unregularized.functionValue;
			final double[] gradient = unregularized.gradient;

			for (int i = 0; i < featureWeights.length; i++) {
				loglikelihood = loglikelihood - (Math.pow(featureWeights[i], 2) / (2.0 * sigmaSquared));
				gradient[i] = gradient[i] - (featureWeights[i] / sigmaSquared);
			}

			final FunctionValues result = new FunctionValues(loglikelihood, gradient);
			return result;
		}

	}

	static abstract class TrainingAlgorithm {
		abstract void train(DifferentiableFunction lossFunction, double[] weights);
	}

	final static class TrainingExample implements Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		private final FeatureForest allParses;
		private final FeatureForest goldParses;

		private final List<InputWord> words;

		TrainingExample(final CompressedChart allParses, final CompressedChart goldParses, final List<InputWord> words,
				final CutoffsDictionaryInterface cutoffsDictionary) {
			super();
			this.goldParses = new FeatureForest(words, goldParses, cutoffsDictionary);
			this.allParses = new FeatureForest(words, allParses, cutoffsDictionary);

			this.words = words;
		}

	}

	static LossFunction getUnregularizedLossFunction(final List<TrainingExample> data,
			final Map<FeatureKey, Integer> featureToIndex, final Training.TrainingParameters trainingParameters,
			final Logger trainingLogger) {
		LossFunction lossFunction = new LogLossFunction(data, trainingParameters.getFeatureSet(), featureToIndex,
				trainingLogger);

		lossFunction = new ParallelLossFunction(lossFunction, Runtime.getRuntime().availableProcessors());
		return lossFunction;
	}

	private static LossFunction getRegularizedAndInvertedLossFunction(LossFunction lossFunction,
			final double sigmaSquared) {
		lossFunction = new RegularizedLossFunction(lossFunction, sigmaSquared);

		lossFunction = new InvertedLossFunction(lossFunction);
		return lossFunction;
	}

	static LossFunction getLossFunction(final List<TrainingExample> data,
			final Map<FeatureKey, Integer> featureToIndex, final Training.TrainingParameters trainingParameters,
			final Logger trainingLogger) throws IOException {
		LossFunction lossFunction = getUnregularizedLossFunction(data, featureToIndex, trainingParameters,
				trainingLogger);

		lossFunction = getRegularizedAndInvertedLossFunction(lossFunction, trainingParameters.getSigmaSquared());

		return lossFunction;
	}

	static TrainingAlgorithm makeLBFGS(final Map<FeatureKey, Integer> featureToIndex,
			final Set<FeatureKey> boundedFeatures) {

		return new TrainingAlgorithm() {
			@Override
			void train(final DifferentiableFunction lossFunction, final double[] weights) {
				try {

					final Minimizer alg = new Minimizer();
					alg.setDebugLevel(5);
					alg.getStopConditions().setFunctionReductionFactor(Math.pow(10, 1)); // 10
					alg.getStopConditions().setMaxIterations(200);

					final List<Bound> bounds = new ArrayList<>();
					final Bound unbounded = new Bound(null, null);
					final Bound max0 = new Bound(null, 0.0);

					bounds.add(unbounded);
					for (int i = 0; i < weights.length - 1; i++) {
						bounds.add(unbounded);
					}

					// Make certain features have an upper bound of 0.
					for (final Entry<FeatureKey, Integer> entry : featureToIndex.entrySet()) {
						if (boundedFeatures.contains(entry.getKey())) {
							bounds.set(entry.getValue(), max0);
						}
					}

					alg.setBounds(bounds);

					final Result result = alg.run(lossFunction, weights);
					System.err.println("Converged after: " + result.iterationsInfo);
					System.arraycopy(result.point, 0, weights, 0, result.point.length);

				} catch (final LBFGSBException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
}