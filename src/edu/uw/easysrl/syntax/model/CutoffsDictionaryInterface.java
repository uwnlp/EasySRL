package edu.uw.easysrl.syntax.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import edu.uw.easysrl.dependencies.SRLFrame.SRLLabel;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Preposition;

public interface CutoffsDictionaryInterface extends Serializable {

	public abstract boolean isFrequent(Category category, int argumentNumber, SRLLabel label);

	public abstract boolean isFrequent(SRLLabel label, int offset);

	public abstract Collection<SRLLabel> getRoles(String word, Category category, Preposition preposition,
			int argumentNumber);

	public abstract boolean isFrequentWithAnySRLLabel(Category category, int argumentNumber);

	public abstract Map<String, Collection<Category>> getTagDict();

	public abstract Collection<Integer> getOffsetsForLabel(SRLLabel label);

}