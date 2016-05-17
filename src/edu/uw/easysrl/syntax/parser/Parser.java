package edu.uw.easysrl.syntax.parser;

import com.google.common.collect.Multimap;

import java.util.List;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.util.Util.Scored;

public interface Parser {

	/**
	 * Ignores the InputReader and parses the supplied list of words.
	 */
	List<Scored<SyntaxTreeNode>> parseTokens(List<String> words);

	List<Scored<SyntaxTreeNode>> doParsing(InputToParser input);

	int getMaxSentenceLength();

	Multimap<Category, UnaryRule> getUnaryRules();
}