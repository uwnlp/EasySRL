package edu.uw.easysrl.syntax.training;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import lbfgsb.LBFGSBException;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;
import edu.uw.easysrl.syntax.model.feature.Clustering;
import edu.uw.easysrl.syntax.training.ClassifierTrainer.AbstractFeature;
import edu.uw.easysrl.syntax.training.PipelineTrainer.TrainingExample;
import edu.uw.easysrl.util.Util;

/*
 * Trains the pipeline model
 */
public class PipelineTrainer extends
ClassifierTrainer<TrainingExample, AbstractFeature<TrainingExample, SRLLabel>, SRLLabel> {

	public static class TrainingExample extends
	edu.uw.easysrl.syntax.training.ClassifierTrainer.AbstractTrainingExample<SRLLabel> {
		private final UnlabelledDependency dep;
		private final List<InputWord> sentence;
		private final SRLLabel label;

		public TrainingExample(final UnlabelledDependency dep, final List<InputWord> sentence, final SRLLabel label) {
			super();
			this.dep = dep;
			this.sentence = sentence;
			this.label = label;
		}

		@Override
		public Collection<SRLLabel> getPossibleLabels() {
			return SRLFrame.getAllSrlLabels();
		}

		@Override
		public SRLLabel getLabel() {
			return label;
		}

	}

	public static void main(final String[] args) throws IOException, LBFGSBException {
		for (final double sigmaSquared : Arrays.asList(0.5)) {
			Util.serialize(new LabelClassifier(new PipelineTrainer().train(3, sigmaSquared)), new File(
					"labelClassifier" + sigmaSquared));

		}
	}

	public static class LabelClassifier implements Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final AbstractClassifier<TrainingExample, AbstractFeature<TrainingExample, SRLLabel>, SRLLabel> classifier;

		public LabelClassifier(
				final AbstractClassifier<TrainingExample, AbstractFeature<TrainingExample, SRLLabel>, SRLLabel> classifier) {
			super();
			this.classifier = classifier;
		}

		public SRLLabel classify(final UnlabelledDependency dep, final List<InputWord> sentence) {
			return classifier.classify(new TrainingExample(dep, sentence, null));
		}
	}

	@Override
	public Collection<AbstractFeature<TrainingExample, SRLLabel>> getFeatures() {
		final Collection<AbstractFeature<TrainingExample, SRLLabel>> result = new ArrayList<>();

		final List<Clustering> clusterings = new ArrayList<>();
		clusterings.add(new Clustering(new File("testfiles/clusters/clusters.20"), false));
		clusterings.add(new Clustering(new File("testfiles/clusters/clusters.50"), false));
		clusterings.add(new Clustering(new File("testfiles/clusters/clusters.250"), false));
		clusterings.add(new Clustering(new File("testfiles/clusters/clusters.1000"), false));
		clusterings.add(new Clustering(new File("testfiles/clusters/clusters.2500"), false));

		result.addAll(edu.uw.easysrl.syntax.model.feature.BilexicalFeature.getBilexicalFeatures(clusterings, 3)
				.stream().map(x -> new BilexicalFeatureAdaptor(x)).collect(Collectors.toList()));

		result.addAll(edu.uw.easysrl.syntax.model.feature.ArgumentSlotFeature.argumentSlotFeatures.stream()
				.map(x -> new ArgumentSlotFeatureAdaptor(x)).collect(Collectors.toList()));

		return result;

	}

	@Override
	public List<TrainingExample> getTrainingData(final boolean isDev) throws IOException {
		final List<TrainingExample> data = new ArrayList<>();
		final Iterator<Sentence> sentenceIt = ParallelCorpusReader.READER.readCorpus(isDev);
		while (sentenceIt.hasNext()) {
			final Sentence sentence = sentenceIt.next();
			final Map<SRLDependency, CCGBankDependency> depsMap = sentence.getCorrespondingCCGBankDependencies();
			final List<Category> cats = sentence.getLexicalCategories();
			final Collection<CCGBankDependency> unlabelled = new HashSet<>(sentence.getCCGBankDependencyParse()
					.getDependencies());
			for (final Entry<SRLDependency, CCGBankDependency> entry : depsMap.entrySet()) {
				final CCGBankDependency ccgbankDep = entry.getValue();
				if (ccgbankDep == null) {
					continue;
				}
				unlabelled.remove(ccgbankDep);
				data.add(new TrainingExample(new UnlabelledDependency(ccgbankDep.getSentencePositionOfPredicate(), cats
						.get(ccgbankDep.getSentencePositionOfPredicate()), ccgbankDep.getArgNumber(), Arrays
						.asList(ccgbankDep.getSentencePositionOfArgument()), Preposition.fromString(entry.getKey()
								.getPreposition())), sentence.getInputWords(), entry.getKey().getLabel()));
			}

			for (final CCGBankDependency dep : unlabelled) {
				String preposition = null;
				final Category predicateCategory = cats.get(dep.getSentencePositionOfPredicate());
				if (predicateCategory.getArgument(dep.getArgNumber()) == Category.PP) {
					for (final CCGBankDependency other : unlabelled) {
						if (other.getSentencePositionOfArgument() == dep.getSentencePositionOfArgument()
								&& cats.get(other.getSentencePositionOfPredicate()) == Category.PREPOSITION) {
							preposition = sentence.getInputWords().get(other.getSentencePositionOfPredicate()).word;
						}
					}
				}
				data.add(new TrainingExample(new UnlabelledDependency(dep.getSentencePositionOfPredicate(), cats
						.get(dep.getSentencePositionOfPredicate()), dep.getArgNumber(), Arrays.asList(dep
								.getSentencePositionOfArgument()), Preposition.fromString(preposition)), sentence
								.getInputWords(), SRLFrame.NONE));
			}
		}
		return data;
	}

	private static class BilexicalFeatureAdaptor extends AbstractFeature<TrainingExample, SRLLabel> {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		public BilexicalFeatureAdaptor(final edu.uw.easysrl.syntax.model.feature.BilexicalFeature bilexicalFeature) {
			super(bilexicalFeature.toString());
			this.bilexicalFeature = bilexicalFeature;
		}

		private final edu.uw.easysrl.syntax.model.feature.BilexicalFeature bilexicalFeature;

		@Override
		public void getValue(final List<Object> result, final TrainingExample trainingExample, final SRLLabel label) {
			for (final int value : bilexicalFeature.getFeatureKey(trainingExample.sentence, label,
					trainingExample.dep.getHead(), trainingExample.dep.getFirstArgumentIndex()).getValues()) {
				result.add(value);
			}
		}
	}

	private static class ArgumentSlotFeatureAdaptor extends AbstractFeature<TrainingExample, SRLLabel> {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		public ArgumentSlotFeatureAdaptor(final edu.uw.easysrl.syntax.model.feature.ArgumentSlotFeature slotFeature) {
			super(slotFeature.toString());
			this.slotFeature = slotFeature;
		}

		private final edu.uw.easysrl.syntax.model.feature.ArgumentSlotFeature slotFeature;

		@Override
		public void getValue(final List<Object> result, final TrainingExample trainingExample, final SRLLabel label) {
			for (final int value : slotFeature.getFeatureKey(trainingExample.sentence, trainingExample.dep.getHead(),
					label, trainingExample.dep.getCategory(), trainingExample.dep.getArgNumber(),
					trainingExample.dep.getPreposition()).getValues()) {
				result.add(value);
			}
		}
	}

}
