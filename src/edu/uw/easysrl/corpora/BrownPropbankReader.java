package edu.uw.easysrl.corpora;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.util.Util;

public class BrownPropbankReader {

	/*
	 *
	 * - (A0*) (A0*) accept (V*) * - (A1* * - *) * - (AM-MNR* * - *) * - * * - *
	 * * - * (AM-NEG*) enter * (V*) - * (A1* - * *) - * *
	 */
	private final static File wordsFile = new File(ParallelCorpusReader.BROWN,
			"test.brown.words");
	private final static File propsFile = new File(ParallelCorpusReader.BROWN,
			"test.brown.props");

	public static void main(final String[] args) throws IOException {
		System.out.println(readCorpus().size());
	}

	public static Collection<SRLParse> readCorpus() throws IOException {
		final Collection<SRLParse> result = new ArrayList<>();

		final Iterator<String> srlLines = Util.readFileLineByLine(propsFile);
		final Iterator<String> wordsLines = Util.readFileLineByLine(wordsFile);

		while (wordsLines.hasNext()) {
			final List<String> words = readWords(wordsLines);
			result.add(readSRLParse(words, srlLines));
		}

		return result;

	}

	private static List<String> readWords(final Iterator<String> lines) {
		final List<String> result = new ArrayList<>();
		while (lines.hasNext()) {
			final String line = lines.next();
			if (line.isEmpty()) {
				break;
			}
			if (result.size() > 0) {
				System.out.print(" ");
			}
			System.out.print(line.trim());
			result.add(line.trim());
		}

		System.out.println();

		return result;
	}

	private static SRLParse readSRLParse(final List<String> words,
			final Iterator<String> lines) {
		int wordNumber = 0;
		final List<Map<SRLLabel, List<Integer>>> predicateToRoleToArgs = new ArrayList<>();
		final List<Integer> numberToPredicateIndex = new ArrayList<>();
		final List<String> numberToPredicate = new ArrayList<>();
		final List<SRLLabel> openRoles = new ArrayList<>();

		while (lines.hasNext()) {
			final String line = lines.next();

			if (line.isEmpty()) {
				break;
			}

			final String[] fields = line.split("\\s+");
			if (fields[0].equals("-")) {
				for (int predNumber = 0; predNumber < fields.length - 1; predNumber++) {
					if (predicateToRoleToArgs.size() <= predNumber) {
						predicateToRoleToArgs.add(new HashMap<>());
					}

					String roleField = fields[predNumber + 1];

					if (!roleField.startsWith("*")
							&& !roleField.equals("(C-V*)")) { // just takes
						// first word
						// for multiword
						// predicsts

						if (roleField.startsWith("(C-")
								|| roleField.startsWith("(R-")) {
							roleField = roleField.substring(2);
						}

						final String roleString = "ARG"
								+ roleField
								.substring(2, roleField.indexOf("*"));
						final SRLLabel role = SRLLabel.fromString(roleString);

						while (openRoles.size() <= predNumber) {
							openRoles.add(null);
						}
						openRoles.set(predNumber, role);

						final Map<SRLLabel, List<Integer>> roleToArgs = predicateToRoleToArgs
								.get(predNumber);
						if (!roleToArgs.containsKey(role)) {
							roleToArgs.put(role, new ArrayList<Integer>());
						}
					}

				}

			} else {
				numberToPredicate.add(fields[0]);
				numberToPredicateIndex.add(wordNumber);
			}

			for (int predNumber = 0; predNumber < openRoles.size(); predNumber++) {
				final SRLLabel role = openRoles.get(predNumber);
				if (role != null) {
					predicateToRoleToArgs.get(predNumber).get(role)
					.add(wordNumber);
				}
			}

			for (int predNumber = 0; predNumber < openRoles.size(); predNumber++) {
				if (fields[predNumber + 1].endsWith("*)")) {
					openRoles.set(predNumber, null);
				}
			}

			wordNumber++;
		}

		final Collection<SRLDependency> srlDeps = new ArrayList<>();
		for (int predNumber = 0; predNumber < numberToPredicate.size(); predNumber++) {
			for (final Entry<SRLLabel, List<Integer>> roleToArgs : predicateToRoleToArgs
					.get(predNumber).entrySet()) {
				srlDeps.add(new SRLDependency(
						numberToPredicate.get(predNumber),
						numberToPredicateIndex.get(predNumber), roleToArgs
						.getValue(), roleToArgs.getKey(), null));
			}
		}

		final SRLParse result = new SRLParse(words, srlDeps);

		return result;
	}
}
