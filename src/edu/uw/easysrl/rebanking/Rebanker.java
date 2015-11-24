package edu.uw.easysrl.rebanking;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import edu.uw.easysrl.corpora.CCGBankDependencies.CCGBankDependency;
import edu.uw.easysrl.corpora.CCGBankParseReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.dependencies.SRLDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.tagger.TagDict;

/**
 * Used to extract modified supertagger training data from CCGBank.
 *
 * @author mlewis
 *
 */
public abstract class Rebanker {

	public static void main(final String[] args) throws IOException {

		Coindexation.parseMarkedUpFile(new File("testfiles/model_rebank/markedup"));

		final boolean isDev = true;
		final File trainingFolder = new File("training/experiments/rebank_again/" + (isDev ? "dev" : "train"));
		trainingFolder.mkdirs();
		processCorpus(isDev, trainingFolder, new DummyRebanker(), new PrepositionRebanker(), new VPadjunctRebanker(),
				new ExtraArgumentRebanker()

		);

	}

	static class DummyRebanker extends Rebanker {

		@Override
		boolean dontUseAsTrainingExample(final Category c) {
			return false;
		}

		@Override
		boolean doRebanking(final List<SyntaxTreeNodeLeaf> result, final Sentence sentence) {
			return false;
		}

	}

	private static void processCorpus(final boolean isDev, final File trainingFolder, final Rebanker... rebankers)
			throws IOException {

		final FileWriter fw = new FileWriter(new File(trainingFolder, "gold.stagged").getAbsoluteFile());
		final BufferedWriter bw = new BufferedWriter(fw);

		final Multimap<String, Category> tagDict = HashMultimap.create();
		getTrainingData(ParallelCorpusReader.READER.readCorpus(isDev), bw, tagDict, isDev, rebankers);

		TagDict.writeTagDict(tagDict.asMap(), new File(trainingFolder, "tagdict.ccgbank"));

		bw.flush();

	}

	private static void getTrainingData(final Iterator<Sentence> corpus, final BufferedWriter supertaggerTrainingFile,
			final Multimap<String, Category> tagDictionary, final boolean isTest, final Rebanker... rebankers)
					throws IOException {
		int changed = 0;
		while (corpus.hasNext()) {
			final Sentence sentence = corpus.next();
			final List<SyntaxTreeNodeLeaf> rebanked = new ArrayList<>(sentence.getCcgbankParse().getLeaves());
			boolean change = false;
			for (final Rebanker rebanker : rebankers) {
				change = change || rebanker.doRebanking(rebanked, sentence);
			}

			if (change) {
				changed++;
				for (final String word : sentence.getWords()) {
					System.out.print(word + " ");
				}
				System.out.println();
				System.out.println(sentence.getCcgbankParse().getLeaves());
				System.out.println(rebanked);
				System.out.println();
			}

			for (final SyntaxTreeNodeLeaf leaf : rebanked) {
				if (leaf.getSentencePosition() > 0) {
					supertaggerTrainingFile.write(" ");
				}

				tagDictionary.put(leaf.getWord(), leaf.getCategory());

				if (!isTest// && dontUseAsTrainingExample(leaf.getCategory())
				) {
					supertaggerTrainingFile.write(leaf.getWord() + "||" + leaf.getCategory());
				} else {
					supertaggerTrainingFile.write(makeDatapoint(leaf));
					/*
					 * if (!isTest) { allCats.add(leaf.getCategory()); }
					 */
				}
			}

			supertaggerTrainingFile.newLine();
		}
		System.out.println("Changed: " + changed);
	}

	private static String makeDatapoint(final SyntaxTreeNodeLeaf leaf) {
		return leaf.getWord() + "|" + leaf.getPos() + "|" + leaf.getCategory();
	}

	abstract boolean dontUseAsTrainingExample(Category c);

	public static SyntaxTreeNode getParse(final Iterator<String> autoLines) {
		String line = autoLines.next();
		line = autoLines.next();
		return CCGBankParseReader.parse(line);
	}

	abstract boolean doRebanking(List<SyntaxTreeNodeLeaf> result, Sentence sentence);

	void setCategory(final List<SyntaxTreeNodeLeaf> result, final int index, final Category newCategory) {
		final SyntaxTreeNodeLeaf leaf = result.get(index);
		result.set(
				index,
				new SyntaxTreeNodeLeaf(leaf.getWord(), leaf.getPos(), leaf.getNER(), newCategory, leaf
						.getSentencePosition()));

	}

	boolean isPropbankCoreArgument(final int predicateIndex, final int argumentNumber, final Sentence sentence,
			final boolean isCore) {
		for (final CCGBankDependency dep : sentence.getCCGBankDependencyParse().getArgument(predicateIndex,
				argumentNumber)) {
			for (final SRLDependency srl : sentence.getSrlParse().getDependenciesAtPredicateIndex(predicateIndex)) {
				if (srl.getArgumentPositions().contains(dep.getSentencePositionOfArgument())
						&& srl.isCoreArgument() == isCore) {
					return true;
				}
			}
		}
		return false;
	}

	boolean isPredicate(final Sentence sentence, final SyntaxTreeNodeLeaf node) {
		if (!sentence.getSrlParse().getPredicatePositions().contains(node.getHeadIndex())) {
			return false;
		}

		// Propbank only annotates adjuncts, not arguments, of "have to"
		for (final SRLDependency dep : sentence.getSrlParse().getDependenciesAtPredicateIndex(node.getHeadIndex())) {
			if (dep.isCoreArgument()) {
				return true;

			}
		}

		return false;
	}
}
