package edu.uw.easysrl.syntax.tagger;

import java.util.Collection;
import java.util.List;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings.ScoredCategory;

public abstract interface Tagger
{



/**
   * Assigned a distribution over lexical categories for a list of words.
   * For each word in the sentence, it returns an ordered list of SyntaxTreeNode representing
   * their category assignment.
   */
  public abstract List<List<ScoredCategory>> tag(List<InputWord> words);

  public abstract Collection<Category> getLexicalCategories();

}