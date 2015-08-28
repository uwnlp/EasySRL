package edu.uw.easysrl.syntax.evaluation;

import java.io.File;
import java.io.IOException;
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
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

import edu.uw.easysrl.corpora.BrownPropbankReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.SRLParse;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.util.Util;

public class CoNLL2008Evaluation {

	public static void main(final String[] args) throws IOException {
		String corpus;
		// corpus = "brown";
		corpus = "wsj";

		final File inputFile = new File(System.getProperty("user.home") + "/Downloads/conll08_submissions/test."
				+ corpus + ".closed");

		final Map<String, Results> scoreToResults = new HashMap<>();
		for (final File file : Util.findAllFiles(new File(System.getProperty("user.home")
				+ "/Downloads/conll08_submissions/"), ".*" + corpus + ".open")) {
			if (!file.getName().startsWith("test.") && !file.getName().startsWith("neumann")
					&& !file.getName().startsWith("trandabat") && !file.getName().startsWith("watanabe")) {
				System.out.println(file.getName());
				try {
					final Results results = evaluate(
							file,
							inputFile,
							corpus.equals("wsj") ? ParallelCorpusReader.getPropBank23() : BrownPropbankReader
									.readCorpus(), false);
					// System.out.println(results);
					scoreToResults.put(file.getName(), results);
				} catch (final Exception e) {
					e.printStackTrace();
				}

			}
		}

		final Ordering<Map.Entry<String, Results>> entryOrdering = Ordering.natural()
				.onResultOf(new Function<Map.Entry<String, Results>, Double>() {
					@Override
					public Double apply(final Map.Entry<String, Results> entry) {
						return entry.getValue().getF1();
					}
				}).reverse();
		// Desired entries in desired order. Put them in an ImmutableMap in this
		// order.
		final ImmutableMap.Builder<String, Results> builder = ImmutableMap.builder();
		for (final Map.Entry<String, Results> entry : entryOrdering.sortedCopy(scoreToResults.entrySet())) {
			builder.put(entry.getKey(), entry.getValue());
		}
		for (final Entry<String, Results> scoreToResult : builder.build().entrySet()) {
			// if (!scoreToResult.getKey().startsWith("posteval")) {
			System.out.println(scoreToResult.getKey() + "\n" + scoreToResult.getValue());

			// }
		}

	}

	public static Results evaluate(final File evaluationFile, final File inputFile,
			final Collection<SRLParse> goldParses, final boolean verbose) throws IOException {

		final Iterator<String> lines = Util.readFileLineByLine(evaluationFile);
		final Iterator<String> inputLines = Util.readFileLineByLine(inputFile);
		final Results results = new Results();
		final int maxLength = 70;
		final int missingSentences = 0;

		final Set<String> sentencesMissingFromCoNLL = new HashSet<>(
				Arrays.asList(
						"[The, well, flowed, at, a, rate, of, 2.016, million, cubic, feet, of, gas, a, day, through, a, 16, 64-inch, opening, at, depths, between, 5,782, and, 5,824, feet, .]",
						"[Petrolane, is, the, second-largest, propane, distributor, in, the, U.S., .]",
						"[In, recent, years, ,, U.S., steelmakers, have, supplied, about, 80, %, of, the, 100, million, tons, of, steel, used, annually, by, the, nation, .]",
						"[Elcotel, will, provide, a, credit-card, reader, for, the, machines, to, collect, ,, store, and, forward, billing, data, .]",
						"[She, added, that, she, expected, ``, perhaps, to, have, a, down, payment, ..., some, small, step, to, convince, the, American, people, and, the, Japanese, people, that, we, 're, moving, in, earnest, ., '']",
						"[Others, said, the, Bush, administration, may, feel, the, rhetoric, on, both, sides, is, getting, out, of, hand, .]",
						"[The, Bush, administration, ,, trying, to, blunt, growing, demands, from, Western, Europe, for, a, relaxation, of, controls, on, exports, to, the, Soviet, bloc, ,, is, questioning, whether, Italy, 's, Ing, ., C., Olivetti, &, Co., supplied, militarily, valuable, technology, to, the, Soviets, .]",
						"[The, brief, attention, viewers, give, CNN, could, put, it, at, a, disadvantage, as, ratings, data, ,, and, advertising, ,, become, more, important, to, cable-TV, channels, .]",
						"[Bush, indicated, there, might, be, ``, room, for, flexibility, '', in, a, bill, to, allow, federal, funding, of, abortions, for, poor, women, who, are, vicitims, of, rape, and, incest, .]",
						"[September, 's, steep, rise, in, producer, prices, shows, that, inflation, still, persists, ,, and, the, pessimism, over, interest, rates, caused, by, the, new, price, data, contributed, to, the, stock, market, 's, plunge, Friday, .]",
						"[Analysts, immediately, viewed, the, price, data, ,, the, grimmest, inflation, news, in, months, ,, as, evidence, that, the, Federal, Reserve, was, unlikely, to, allow, interest, rates, to, fall, as, many, investors, had, hoped, .]",
						"[Although, all, the, price, data, were, adjusted, for, normal, seasonal, fluctuations, ,, car, prices, rose, beyond, the, customary, autumn, increase, .]",
						"[It, said, the, sale, would, give, it, positive, tangible, capital, of, $, 82, million, ,, or, about, 1.2, %, of, assets, ,, from, a, negative, $, 33, million, as, of, Sept., 30, ,, thus, bringing, CenTrust, close, to, regulatory, standards, .]",
						"[In, the, second, quarter, ,, the, steelmaker, had, net, income, of, $, 45.3, million, or, $, 1.25, a, share, ,, including, a, pretax, charge, of, $, 17, million, related, to, the, settlement, of, a, suit, ,, on, sales, of, $, 1.11, billion, .]",
						"[As, your, editorial, rightly, pointed, out, ,, Samuel, Pierce, ,, former, HUD, secretary, ,, and, Lance, Wilson, ,, Mr., Pierce, 's, former, aide, ,, ``, are, currently, being, held, up, to, scorn, for, taking, the, Fifth, Amendment, .]",
						"[``, There, is, an, underlying, concern, on, the, part, of, the, American, people, --, and, there, should, be, -, that, the, administration, has, not, gone, far, enough, in, cutting, this, deficit, and, that, Congress, has, been, unwilling, to, cut, what, the, administration, asked, us, to, cut, ,, '', said, Senate, Finance, Committee, Chairman, Lloyd, Bentsen, -LRB-, D., ,, Texas, -RRB-, .]",
						"[Mrs., Crump, said, her, Ashwood, Investment, Club, 's, portfolio, lost, about, one-third, of, its, value, following, the, Black, Monday, crash, ,, ``, but, no, one, got, discouraged, ,, and, we, gained, that, back, --, and, more, ., '']",

						"[Trig, and, a, very, black, colored, boy, from, Detroit, had, killed, or, put, out, of, action, ten, guerrillas, by, grenades, and, hand-to-hand, fighting, .]"));

		int evalSentences = 0;

		final Iterator<SRLParse> goldParsesIt = goldParses.iterator();
		while (goldParsesIt.hasNext()) {
			final SRLParse goldParse = goldParsesIt.next();

			final List<String> words = goldParse.getWords();

			if (sentencesMissingFromCoNLL.contains(words.toString())) {
				// No idea why these are missing.
				continue;
			}

			final List<String> foundWords = new ArrayList<>();
			final Set<SRLDependency> predictedDeps = parseConll(foundWords, lines, inputLines);

			if (foundWords.size() == 0) {
				break;
			}
			if (verbose) {
				System.out.println(words);

			}

			if (words.size() != foundWords.size()) {
				System.out.println(words);
				System.out.println(foundWords);
				throw new RuntimeException("Sentence missing from Conll");
			}

			if (words.size() >= maxLength) {
				continue;
			}

			final Set<SRLDependency> goldDeps = new HashSet<>(goldParse.getDependencies());
			int correct = 0;
			for (final SRLDependency predictedDep : predictedDeps) {
				SRLDependency matchingDep = null;
				for (final SRLDependency goldDep : goldDeps) {
					if (predictedDep.getPredicateIndex() == goldDep.getPredicateIndex()
							&& predictedDep.getLabel() == goldDep.getLabel()
							&& goldDep.getArgumentPositions().containsAll(predictedDep.getArgumentPositions())) {
						correct++;
						matchingDep = goldDep;
						if (verbose) {
							System.out.println(goldDep.toString(words));
							System.out.println(predictedDep.toString(words));

						}

						break;
					}
				}

				if (matchingDep != null) {
					goldDeps.remove(matchingDep);
				}
			}
			if (verbose) {
				for (final SRLDependency missing : goldDeps) {
					System.out.println("MISSING: " + missing.toString(words));
				}
			}

			evalSentences++;
			results.add(new Results(predictedDeps.size(), correct, goldParse.getDependencies().size()));
			if (verbose) {
				System.out.println(results);
			}
		}

		if (verbose) {
			System.out.println("Missing sentences     = " + missingSentences);
			System.out.println("Evaluated sentences   = " + evalSentences);

		}
		return results;
	}

	public static Set<SRLDependency> parse(final Iterator<String> lines) {
		final Set<SRLDependency> result = new HashSet<>();
		while (lines.hasNext()) {
			final String line = lines.next();

			if (line.isEmpty()) {
				break;
			}

			if (line.equals("<no deps>")) {
				lines.next();
				return Collections.emptySet();
			}

			final String[] fields = line.split(" ");
			String label = fields[1];
			if (label.startsWith("P:") && !label.equals("P:SU") // Drop support
			// links
			) {
				label = label.substring(2);
				label = "ARG" + label.substring(1);

				result.add(new SRLDependency(getString(fields[0]), getIndex(fields[0]), Arrays
						.asList(getIndex(fields[2])), SRLLabel.fromString(label), null));
			}
		}

		return result;
	}

	public static Set<SRLDependency> parseConll(final List<String> foundWords, final Iterator<String> lines,
			final Iterator<String> inputLines) {
		final List<Integer> predicates = new ArrayList<>();
		final List<List<SRLLabel>> labels = new ArrayList<>();
		final List<List<Integer>> arguments = new ArrayList<>();
		final List<String> words = new ArrayList<>();
		final int predColumn = 3;
		// List<String> foundWords = new ArrayList<>();
		int wordNumber = 1;

		while (lines.hasNext()) {
			final String inputLine = inputLines.next();
			final String outputLine = lines.next();
			// System.out.println(" " + line);
			if (outputLine.isEmpty()) {
				break;
			}

			/*
			 * 31 33 SBJ _ _ _ A0 _ _ 32 33 MNR _ _ _ AM-MNR _ _ 33 19 COORD manage.02 _ _ _ _ _ 34 33 OPRD _ _ _ A1 _ _
			 * 35 34 IM stay.01 _ _ _ _ _ 36 37 NMOD _ _ _ _ _ _ 37 35 OBJ side.01 _ _ _ A1 _
			 */

			/*
			 * 7 's 's _ POS 's 's POS 8 500-stock 500-stock _ JJ 500 500 CD 9 _ _ _ _ - - HYPH 10 _ _ _ _ stock stock
			 * NN 11 index index _ NN index index NN 12 futures future _ NNS futures future NNS
			 */
			final String[] fields = outputLine.split("\t");
			final String[] inputFields = inputLine.split("\t");

			final int word = wordNumber;

			words.add(inputFields[1]);

			if (inputFields[1].equals("_")) {
				// Split words
			} else {
				foundWords.add(inputFields[1]);
				wordNumber++;
			}

			if (!fields[predColumn].equals("_")) {
				if (!inputFields[4].startsWith("V")) {
					// Ignore nominal predicates
					predicates.add(null);
				} else {
					predicates.add(word);
				}

				while (predicates.size() >= labels.size()) {
					labels.add(new ArrayList<>());
					arguments.add(new ArrayList<>());
				}
			}

			for (int i = predColumn + 1; i < fields.length; i++) {
				String label = fields[i];
				if (!label.equals("_")) {
					final int predNumber = i - predColumn - 1;
					while (predNumber >= labels.size()) {
						labels.add(new ArrayList<>());
						arguments.add(new ArrayList<>());
					}

					if (!label.startsWith("R-") && !label.startsWith("C-") && !label.equals("SU")) {
						// A0 --> ARG0
						label = "ARG" + label.substring(1);
						labels.get(predNumber).add(SRLLabel.fromString(label));
						arguments.get(predNumber).add(word);

					}
				}
			}

		}
		final Set<SRLDependency> result = new HashSet<>();
		for (int i = 0; i < predicates.size(); i++) {
			if (predicates.get(i) == null) {
				continue; // Nominal predicates
			}

			final int predicateIndex = predicates.get(i) - 1;
			final List<Integer> args = arguments.get(i);
			final List<SRLLabel> labs = labels.get(i);
			for (int j = 0; j < args.size(); j++) {
				result.add(new SRLDependency(words.get(predicateIndex), predicateIndex, Arrays.asList(args.get(j) - 1),
						labs.get(j), null));
			}
		}

		return result;

	}

	private static String getString(final String field) {
		return field.substring(0, field.indexOf("_"));
	}

	private static int getIndex(final String field) {
		return Integer.valueOf(field.substring(field.lastIndexOf("_") + 1, field.lastIndexOf('.')));
	}
}
