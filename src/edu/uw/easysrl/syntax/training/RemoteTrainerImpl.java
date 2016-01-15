package edu.uw.easysrl.syntax.training;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.training.Optimization.LossFunction;
import edu.uw.easysrl.syntax.training.Optimization.TrainingExample;
import edu.uw.easysrl.syntax.training.TrainingDataLoader.TrainingDataParameters;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Logger;

public class RemoteTrainerImpl extends UnicastRemoteObject implements
RemoteTrainer {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	protected RemoteTrainerImpl() throws RemoteException {
		super();
	}

	private LossFunction lossFunction;

	@Override
	public String getName() {

		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (final UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void loadData(final int firstSentence, final int lastSentence,
			final TrainingDataParameters dataParameters,
			final File modelFolder, final Logger trainingLogger)
			throws IOException, RemoteException {

		System.out.println("Loading sentences from: " + firstSentence + "\t"
				+ lastSentence);
		final Collection<Sentence> sentences = new ArrayList<>();
		final Iterator<Sentence> sentenceIt = ParallelCorpusReader.READER
				.readCorpus(false);
		int i = 0;
		while (sentenceIt.hasNext()) {
			final Sentence sentence = sentenceIt.next();
			if (i < firstSentence) {

			} else if (i == lastSentence) {
				break;
			} else {
				System.out.println("Sentence "
						+ sentence.getCCGBankDependencyParse().getFile()
						+ "."
						+ sentence.getCCGBankDependencyParse()
						.getSentenceNumber());
				sentences.add(sentence);

			}

			i++;
		}

		final Training.TrainingParameters trainingParameters = Util
				.deserialize(new File(modelFolder, "parameters"));
		final CutoffsDictionaryInterface cutoffs = Util.deserialize(new File(
				modelFolder, "cutoffs"));
		final Map<FeatureKey, Integer> featureToIndex = Util
				.deserialize(trainingParameters.getFeatureToIndexFile());

		final List<TrainingExample> data = new TrainingDataLoader(cutoffs,
				dataParameters, true).makeTrainingData(sentences.iterator(),
				false);
		lossFunction = Optimization.getUnregularizedLossFunction(data,
				featureToIndex, trainingParameters, trainingLogger);
	}

	@Override
	public FunctionValuesSerializable getValues(final double[] weights) {
		return new FunctionValuesSerializable(lossFunction.getValues(weights));
	}

	public static void main(final String args[]) throws RemoteException,
	MalformedURLException, UnknownHostException {
		final String hostName = InetAddress.getLocalHost().getHostName();
		System.out.println("Using server: " + hostName);
		System.setProperty("java.rmi.server.hostname", hostName);

		LocateRegistry.createRegistry(1099);
		final RemoteTrainerImpl trainer = new RemoteTrainerImpl();
		Naming.rebind("RemoteTrainer", trainer);

	}

}
