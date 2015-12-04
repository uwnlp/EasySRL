package edu.uw.easysrl.semantics.lexicon;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;

public class CompositeLexicon extends Lexicon {

	private final List<Lexicon> lexica;

	private CompositeLexicon(final List<Lexicon> lexica) {
		this.lexica = ImmutableList.copyOf(lexica);
	}

	public static Lexicon makeDefault(final File... manualLexiconFiles) throws IOException {
		final List<Lexicon> lexica = new ArrayList<>();
		for (final File file : manualLexiconFiles) {
			lexica.add(new ManualLexicon(file));
		}
		lexica.add(new CopulaLexicon());
		lexica.add(new NumbersLexicon());
		lexica.add(new DefaultLexicon());

		return new CompositeLexicon(lexica);
	}

	@Override
	public Logic getEntry(final String word, final String pos, final Category category, final Coindexation coindexation,
			final Optional<CCGandSRLparse> parse, final int wordIndex) {
		Logic result = null;
		for (final Lexicon lexicon : lexica) {
			result = lexicon.getEntry(word, pos, category, coindexation, parse, wordIndex);
			if (result != null) {
				break;
			}
		}

		return result;
	}

	@Override
	public boolean isMultiWordExpression(final SyntaxTreeNode node) {
		return lexica.stream().anyMatch(x -> x.isMultiWordExpression(node));
	}
}
