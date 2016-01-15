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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Stopwatch;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.SRLParse;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame;
import edu.uw.easysrl.main.EasySRL;
import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.main.ParsePrinter;
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
		final PipelineSRLParser pipeline = new PipelineSRLParser(EasySRL.makeParser(pipelineFolder, 0.00001,
				ParsingAlgorithm.ASTAR, 200000, false, Optional.empty(), 1, 70), Util.deserialize(new File(
				pipelineFolder, "labelClassifier")), posTagger);

		for (final double beta : Arrays.asList(0.01, 0.005, 0.001)) {
			// for (final Double supertaggerWeight : Arrays.asList(null)) {
			final Double supertaggerWeight = null;
			// final SRLParser jointAstar = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, beta,
			// ParsingAlgorithm.ASTAR, 100000, true, Optional.empty(), 1), posTagger), pipeline);
			final SRLParser jointAstar = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, beta,
					ParsingAlgorithm.ASTAR, 20000, true,
					supertaggerWeight == null ? Optional.empty() : Optional.of(supertaggerWeight), 1, 70), posTagger),
					pipeline);

			// final SRLParser jointCKY = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, 0.01,
			// ParsingAlgorithm.CKY, 400000, true, Optional.empty(), 0), posTagger), pipeline);
			//
			// final SRLParser jointAST = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, 0.1,
			// ParsingAlgorithm.CKY, 400000, true), posTagger), new JointSRLParser(EasySRL.makeParser(folder, 0.01,
			// ParsingAlgorithm.CKY, 400000, true), posTagger), pipeline);
			//
			// final SRLParser parser = new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, 0.01,
			// ParsingAlgorithm.ASTAR, 20000, true), posTagger), pipeline);
			// CCGBankEvaluation.evaluate(pipeline, false);

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
		while (depsIt.hasNext()) {
			final ResolvedDependency dep = depsIt.next();
			if (((dep.getSemanticRole() == SRLFrame.NONE)) || dep.getOffset() == 0 // Unrealized
					// arguments
					) {
				depsIt.remove();
			}
		}

		// for (final SyntaxTreeNodeLeaf leaf : parse.getCcgParse().getLeaves()) {
		// System.err.print(leaf.getWord() + "|" + leaf.getCategory() + " ");
		// }
		// System.err.println();

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

			// if (!found) {
			// System.err.println("missing:" + goldDep.toString(gold.getWords()));
			// }
		}
		// for (final ResolvedDependency wrong : predictedDeps) {
		// System.err.println("wrong:  " + wrong.toString(gold.getWords()));
		// }

		return new Results(predictedCount, correctCount, goldCount);
	}

}
