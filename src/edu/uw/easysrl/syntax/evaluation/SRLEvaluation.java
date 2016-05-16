package edu.uw.easysrl.syntax.evaluation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Stopwatch;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.SRLParse;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.BackoffSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.syntax.parser.SRLParser.JointSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

public class SRLEvaluation {

	public static void main(final String[] args) throws IOException {

		final String folder = Util.getHomeFolder() + "/Downloads/lstm_models/model";
		final String pipelineFolder = folder + "/pipeline";
		final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));
		final PipelineSRLParser pipeline = new PipelineSRLParser(new ParserAStar.Builder(new File(pipelineFolder))
				.supertaggerBeam(0.00001).build(), Util.deserialize(new File(pipelineFolder, "labelClassifier")),
				posTagger);

		for (final double beta : Arrays.asList(0.01, 0.005, 0.001)) {
			// for (final Double supertaggerWeight : Arrays.asList(null)) {
			final Double supertaggerWeight = null;

			final SRLParser jointAstar = new BackoffSRLParser(

			new JointSRLParser(new ParserAStar.Builder(new File(folder)).maxChartSize(20000).supertaggerBeam(beta)
							.supertaggerWeight(supertaggerWeight).build(), posTagger), pipeline);

			evaluate(jointAstar,
			// pipeline,
			// // BrownPropbankReader.readCorpus()//
					ParallelCorpusReader.getPropBank00()
					// ParallelCorpusReader.getPropBank23()
					, 70);
			// }
		}
	}

	public static Results evaluate(final SRLParser parser, final Collection<SRLParse> iterator,
			final int maxSentenceLength) throws FileNotFoundException {
		final List<String> autoOutput = new ArrayList<>();

		final Results results = new Results();
		int id = 0;

		final Collection<List<String>> failedToParse = new ArrayList<>();
		final AtomicInteger shouldParse = new AtomicInteger();
		final AtomicInteger parsed = new AtomicInteger();

		final Collection<Runnable> jobs = new ArrayList<>();

		final boolean oneThread = true;

		System.out.println("Parsing...");
		final Stopwatch stopwatch = Stopwatch.createStarted();

		for (final SRLParse srlParse : iterator) {
			id++;
			final List<CCGandSRLparse> parses = parser.parseTokens(InputWord.listOf(srlParse.getWords()));
			if (parses == null || parses.size() == 0) {
				if (srlParse.getWords().size() < maxSentenceLength) {
					failedToParse.add(srlParse.getWords());

				}

				results.add(new Results(0, 0, srlParse.getDependencies().size()));

				continue;
			} else {
				final CCGandSRLparse parse = parses.get(0);
				autoOutput.add(ParsePrinter.CCGBANK_PRINTER.print(parse != null ? parse.getCcgParse() : null, id));

				parsed.getAndIncrement();

				results.add(evaluate(srlParse, parse));
			}

		}

		if (!oneThread) {
			Util.runJobsInParallel(jobs, Runtime.getRuntime().availableProcessors());

		}

		for (final List<String> cov : failedToParse) {
			System.err.print("FAILED TO PARSE: ");
			for (final String word : cov) {
				System.err.print(word + " ");
			}
			System.err.println();
		}

		System.out.println(results);

		System.out.println("Coverage: " + Util.twoDP(100.0 * parsed.get() / shouldParse.get()));
		System.out.println("Time: " + stopwatch.elapsed(TimeUnit.SECONDS));

		return results;
	}

	private static Results evaluate(final SRLParse gold, final CCGandSRLparse parse) {
		final Collection<ResolvedDependency> predictedDeps = new HashSet<>(parse.getDependencyParse());

		final Set<SRLDependency> goldDeps = new HashSet<>(gold.getDependencies());
		final Iterator<ResolvedDependency> depsIt = predictedDeps.iterator();
		// Remove non-SRL dependencies.
		predictedDeps.removeIf(dep -> (dep.getSemanticRole() == SRLFrame.NONE) || dep.getOffset() == 0);

		int correctCount = 0;
		final int predictedCount = predictedDeps.size();
		final int goldCount = goldDeps.size();
		for (final SRLDependency goldDep : goldDeps) {
			if (goldDep.getArgumentPositions().size() == 0) {
				continue;
			}

			// boolean found = false;
			for (final ResolvedDependency predictedDep : predictedDeps) {
				int predictedPropbankPredicate;
				int predictedPropbankArgument;
				if (goldDep.isCoreArgument()) {
					predictedPropbankPredicate = predictedDep.getHead();
					predictedPropbankArgument = predictedDep.getArgumentIndex();
				} else {
					predictedPropbankPredicate = predictedDep.getArgumentIndex();
					predictedPropbankArgument = predictedDep.getHead();
				}
				// For adjuncts, the CCG functor is the Propbank argument

				if (goldDep.getPredicateIndex() == predictedPropbankPredicate
						&& (goldDep.getLabel() == predictedDep.getSemanticRole())
						&& goldDep.getArgumentPositions().contains(predictedPropbankArgument)) {

					predictedDeps.remove(predictedDep);
					correctCount++;
					// found = true;
					break;
				}
			}
		}

		return new Results(predictedCount, correctCount, goldCount);
	}
}
