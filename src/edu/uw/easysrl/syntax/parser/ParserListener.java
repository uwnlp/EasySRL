package edu.uw.easysrl.syntax.parser;

import java.util.List;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.util.Util.Scored;

public interface ParserListener {
	void handleNewSentence(final List<InputWord> words);

	// Returns whether or not to keep parsing.
	boolean handleChartInsertion(final Agenda agenda);

	void handleSearchCompletion(final List<Scored<SyntaxTreeNode>> result, final Agenda agenda, final int chartSize);
}
