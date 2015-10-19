package edu.uw.easysrl.dependencies;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.collect.Multiset;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util;

public class MarkedUpFileNormalizer {
	public static void main(final String[] args) throws IOException {
		// System.out.println(normalize("(S\\NP)/NP"));
		// System.out.println(normalize("(N_1\\N_1)/S[inv]"));
		// System.out.println(normalize("(N_1\\N_1)/(S_3\\NP_1)_3"));
		//
		// System.out.println(normalize("NP/(S[b]_102\\NP{_*})_102"));
		// System.out.println(normalize("S_1/S_1"));
		// System.out.println(normalize("S/S"));
		// System.out.println(normalize("S\\NP"));
		// System.out.println(normalize("(S\\NP)/NP"));
		// System.out.println(normalize("(S_2\\NP_3)_2/(S_2\\NP_3)_2"));
		// System.out.println(normalize("((S_2\\NP_1)_2/(S_4\\NP_3)_4)/NP_3"));

		final Set<Category> categories = new HashSet<>(TaggerEmbeddings.loadCategories(new File(
				"testfiles/model_rebank_extra_args/categories")));
		final Map<Category, Collection<Example>> examples = findExampleSentences(categories);
		parseMarkedUpFile(new File("testfiles/model_rebank_extra_args/markedup"), categories, examples);
	}

	private static class Example {
		private final Sentence sentence;
		private final int index;

		public Example(final Sentence sentence, final int index) {
			super();
			this.sentence = sentence;
			this.index = index;
		}
	}

	private final static Comparator<Example> exampleSentenceComparator = new Comparator<Example>() {

		@Override
		public int compare(final Example o1, final Example o2) {
			return ComparisonChain
					.start()
					// Prefer non-punctuation
					.compareTrueFirst(o1.sentence.getInputWords().get(o1.index).word.matches("[a-zA-Z]*"),
							o2.sentence.getInputWords().get(o2.index).word.matches("[a-zA-Z]*"))
							// Prefer at least 5 word sentences
					.compareTrueFirst(o1.sentence.getLength() >= 5, o2.sentence.getLength() >= 5)
							// Prefer shorter sentences
					.compare(o1.sentence.getLength(), o2.sentence.getLength()).result();
		}

	};

	private static final Map<Category, Collection<Example>> findExampleSentences(final Set<Category> categories)
			throws IOException {
		final Map<Category, Collection<Example>> result = new HashMap<>();
		final Iterator<Sentence> sentenceIt = ParallelCorpusReader.READER.readCorpus(false);
		while (sentenceIt.hasNext()) {
			final Sentence sentence = sentenceIt.next();
			final Collection<Category> foundInSentence = new HashSet<>();
			for (final SyntaxTreeNodeLeaf leaf : sentence.getCcgbankParse().getLeaves()) {
				if (categories.contains(leaf.getCategory()) && !foundInSentence.contains(leaf.getCategory())) {
					Collection<Example> sentences = result.get(leaf.getCategory());

					if (sentences == null) {
						sentences = MinMaxPriorityQueue.orderedBy(exampleSentenceComparator).maximumSize(10).create();
						result.put(leaf.getCategory(), sentences);
					}

					foundInSentence.add(leaf.getCategory());

					boolean haveDuplicate = false;
					for (final Example other : sentences) {
						// Avoid duplicates
						if (other.sentence.getSrlParse().getWords().equals(sentence.getSrlParse().getWords())) {
							haveDuplicate = true;
						}
					}

					if (!haveDuplicate) {
						sentences.add(new Example(sentence, leaf.getHeadIndex()));
					}
				}
			}
		}
		return result;
	}

	private static void printExampleSentence(final Category category, final Example example) {
		System.out.print("# ");

		if (example != null) {

			final Map<Integer, String> indexToAnnotation = new HashMap<>();
			for (int i = 1; i <= category.getNumberOfArguments(); i++) {
				if (category.getArgument(i).isFunctionInto(Category.valueOf("S\\NP"))) {
					// VP arguments
					Util.debugHook();
					for (final CCGBankDependency depToVP : example.sentence.getCCGBankDependencyParse().getArgument(
							example.index, i)) {
						final int vpIndex = depToVP.getSentencePositionOfArgument();
						if (example.sentence.getSrlParse().getPredicatePositions().contains(vpIndex)) {
							for (final SRLDependency dep : example.sentence.getSrlParse()
									.getDependenciesAtPredicateIndex(vpIndex)) {

								if (!dep.isCoreArgument()) {
									continue;
								}

								for (final CCGBankDependency arg : example.sentence.getCCGBankDependencyParse()
										.getDependencies(example.index)) {
									if (dep.getArgumentPositions().contains(arg.getSentencePositionOfArgument())) {
										indexToAnnotation
												.put(arg.getSentencePositionOfArgument(), "_" + dep.getLabel());
										indexToAnnotation.put(vpIndex, "_P");
									}

								}
								if (!indexToAnnotation.containsKey(vpIndex)
										&& dep.getArgumentPositions().contains(example.index)) {
									indexToAnnotation.put(example.index, "_" + dep.getLabel());
									indexToAnnotation.put(vpIndex, "_P");
								}
							}

						}
					}

				}
			}

			boolean hadCategory = false;
			for (final SyntaxTreeNodeLeaf word : example.sentence.getCcgbankParse().getLeaves()) {
				String annotation = indexToAnnotation.get(word.getHeadIndex());
				if (annotation == null) {
					annotation = "";
				}
				if (category == word.getCategory() && !hadCategory) {
					hadCategory = true;
					System.out.print(word.getWord() + annotation + "|" + category);
				} else {
					System.out.print(word.getWord() + annotation);
				}

				System.out.print(" ");
			}

			System.out.println();

		}

	}

	public static void parseMarkedUpFile(final File file, final Set<Category> categories,
			final Map<Category, Collection<Example>> examples) throws IOException {
		final Iterator<String> lines = Util.readFileLineByLine(file);
		final List<String> output = new ArrayList<>();
		final Set<Category> seenCategories = new HashSet<>();
		while (lines.hasNext()) {
			String line = lines.next();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			line = line.replaceAll(":B", "");
			line = line.replaceAll(":U", "");

			final Category category = Category.valueOf(line);
			if (categories.contains(category)) {
				seenCategories.add(category);
				final DependencyStructure coindexation1 = DependencyStructure.fromString(category, line, 0);
				final DependencyStructure coindexation2 = DependencyStructure.fromString(category, normalize(line), 0);
				if (!coindexation1.equals(coindexation2)) {
					System.out.println(line);
					System.out.println(coindexation1.getCoindexation());
					System.out.println(coindexation2.getCoindexation());
					System.out.println();
				}
				// Preconditions.checkState(coindexation1.equals(coindexation2));
				// Preconditions.checkState(coindexation1.equals(coindexation2));
				// System.out.println(coindexation2.getCoindexation());
				// printExampleSentence(category, examples.get(category));
				// System.out.println(normalize(line));

				output.add(normalize(line));
			}

		}

		Preconditions.checkState(categories.equals(seenCategories));
		Collections.sort(output, new Comparator<String>() {

			@Override
			public int compare(final String o1, final String o2) {
				return dropBrackets(o1).compareTo(dropBrackets(o2));
			}

			private String dropBrackets(final String o1) {
				return o1.replaceAll("\\(", "").replaceAll("\\)", "");
			}

		});
		for (final String line : output) {
			final Category category = Category.valueOf(line);
			final Collection<Example> examplesForCategory = examples.get(category);
			if (examplesForCategory != null) {
				for (final Example sentence : examplesForCategory) {
					printExampleSentence(category, sentence);

				}
			} else {
				System.out.println("# TODO no examples found");
			}

			if (line.matches(".*[A-Z].*S[^aA-Z]*NP\\).*")
					&& Util.indexOfAny(line.substring(Util.indexOfAny(line, "NP") + 2), "[NP]") > -1) {
				// VP arguments missing a subject.
				System.out.println("# TODO check this");
			}
			System.out.println(line);

			System.out.println();
		}
	}

	public static String normalize(final String input) {
		final Category category = Category.valueOf(input);
		final Coindexation coindexation = DependencyStructure.fromString(category, input, 0).getCoindexation();

		final Multiset<Integer> indexCount = HashMultiset.create();
		countIndices(coindexation, indexCount);
		final StringBuilder result = new StringBuilder();
		final List<Integer> duplicates = indexCount.entrySet().stream().filter(x -> x.getCount() >= 2)
				.map(x -> x.getElement()).collect(Collectors.toList());

		makeString(category, coindexation, result, true, duplicates, true);
		return result.toString();

	}

	private static void countIndices(final Coindexation coindexation, final Multiset<Integer> result) {
		final Integer id = coindexation.idOrHead.id;
		if (id != null && id != 0) {
			result.add(id);
		}

		if (coindexation.left != null) {
			countIndices(coindexation.left, result);
		}
		if (coindexation.right != null) {
			countIndices(coindexation.right, result);
		}
	}

	private static void makeString(final Category category, final Coindexation coindexation,
			final StringBuilder result, final boolean isFirst, final List<Integer> duplicateIndices,
			final boolean onSpine) {
		if (category.getNumberOfArguments() == 0) {
			result.append(category);

		} else {
			if (!isFirst) {
				result.append("(");
			}

			makeString(category.getLeft(), coindexation.left, result, false, duplicateIndices, onSpine);
			result.append(category.getSlash());
			makeString(category.getRight(), coindexation.right, result, false, duplicateIndices, false);

			if (!isFirst) {
				result.append(")");
			}
		}

		final Integer id = coindexation.idOrHead.id;
		if (duplicateIndices.contains(id)) {
			result.append("_" + (duplicateIndices.indexOf(id) + 1));
		}

		if (id == null && !onSpine) {
			result.append("{_*}");
		}
	}
}
