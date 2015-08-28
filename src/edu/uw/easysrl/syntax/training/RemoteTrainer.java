package edu.uw.easysrl.syntax.training;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import edu.uw.easysrl.syntax.training.TrainingDataLoader.TrainingDataParameters;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Logger;
import lbfgsb.DifferentiableFunction;
import lbfgsb.FunctionValues;

/**
 * Interface for distributed training workers
 *
 */
public interface RemoteTrainer extends Remote {

	public static Collection<RemoteTrainer> getTrainers(
			final List<String> servers) throws MalformedURLException,
			RemoteException, NotBoundException {

		final Collection<RemoteTrainer> result = new ArrayList<>();
		for (final String server : servers) {
			final String url = "rmi://" + server + "/RemoteTrainer";
			result.add((RemoteTrainer) Naming.lookup(url));
		}

		return result;

	}

	/**
	 * Computes the gradient for the worker's shard of the training data, using
	 * the specified parameter vector
	 */
	public FunctionValuesSerializable getValues(double[] weights)
			throws RemoteException;

	/**
	 * Tells the worker to create and store training data from the specified
	 * range of the training corpus
	 */
	void loadData(int firstSentence, int lastSentence,
			TrainingDataParameters dataParameters, File modelFolders,
			Logger trainingLogger) throws IOException, RemoteException;

	String getName() throws RemoteException;

	/**
	 * Serializable wrapper of the result of an iteration, that can be sent over
	 * the RMI
	 *
	 */
	static class FunctionValuesSerializable implements Serializable {

		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		private final double functionValue;
		private final double[] gradient;

		FunctionValuesSerializable(final FunctionValues values) {
			this.functionValue = values.functionValue;
			this.gradient = values.gradient;
		}

		FunctionValues toFunctionValues() {
			return new FunctionValues(functionValue, gradient);
		}

	}
}

class DistributedLossFunction implements DifferentiableFunction {
	private final Collection<RemoteTrainer> servers;

	private int it = 1;

	private final double sigmaSquared;

	public DistributedLossFunction(final Collection<RemoteTrainer> servers,
			final double sigmaSquared) {
		this.servers = servers;
		this.sigmaSquared = sigmaSquared;
		System.out.println("SigmaSquared = " + sigmaSquared);
	}

	@Override
	public FunctionValues getValues(final double[] featureWeights) {

		final Collection<Callable<FunctionValues>> tasks = new ArrayList<>();

		for (final RemoteTrainer trainer : servers) {
			tasks.add(new Callable<FunctionValues>() {
				@Override
				public FunctionValues call() throws Exception {
					return trainer.getValues(featureWeights).toFunctionValues();
				}
			});
		}

		final ExecutorService executor = Executors.newFixedThreadPool(servers
				.size());
		List<Future<FunctionValues>> results;
		try {
			results = executor.invokeAll(tasks);

			final double[] totalGradient = new double[featureWeights.length];
			double totalLoss = 0.0;

			for (final Future<FunctionValues> result : results) {
				final FunctionValues values = result.get();
				totalLoss += values.functionValue;
				Util.add(totalGradient, values.gradient);
				System.out.print(".");
			}
			executor.shutdown(); // always reclaim resources

			for (int i = 0; i < featureWeights.length; i++) {
				totalLoss = totalLoss
						- (Math.pow(featureWeights[i], 2) / (2.0 * sigmaSquared));
				totalGradient[i] = totalGradient[i]
						- (featureWeights[i] / sigmaSquared);
			}

			totalLoss = -totalLoss;
			for (int i = 0; i < featureWeights.length; i++) {
				totalGradient[i] = -totalGradient[i];
			}

			System.out.println();

			System.out.println("Iteration: " + (it++));
			System.out.println("Loss     = " + Util.twoDP(totalLoss));
			return new FunctionValues(totalLoss, totalGradient);

		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}

	}
}
