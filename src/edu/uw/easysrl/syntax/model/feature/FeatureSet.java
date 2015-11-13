package edu.uw.easysrl.syntax.model.feature;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableSet;

import edu.uw.easysrl.syntax.model.feature.Feature.BinaryFeature;
import edu.uw.easysrl.syntax.model.feature.Feature.RootCategoryFeature;
import edu.uw.easysrl.syntax.model.feature.Feature.UnaryRuleFeature;

public class FeatureSet implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 4112311242618770896L;

	public FeatureSet(final DenseLexicalFeature lexicalCategoryFeatures,
			final Collection<BilexicalFeature> dependencyFeatures,
			final Collection<ArgumentSlotFeature> argumentSlotFeatures,
			final Collection<UnaryRuleFeature> unaryRuleFeatures,
			final Collection<PrepositionFeature> prepositionFeatures,
			final Collection<RootCategoryFeature> rootFeatures, final Collection<BinaryFeature> binaryFeatures) {
		super();
		this.lexicalCategoryFeatures = lexicalCategoryFeatures;
		this.dependencyFeatures = ImmutableSet.copyOf(dependencyFeatures);
		this.argumentSlotFeatures = ImmutableSet.copyOf(argumentSlotFeatures);
		this.unaryRuleFeatures = unaryRuleFeatures;
		this.prepositionFeatures = ImmutableSet.copyOf(prepositionFeatures);
		this.rootFeatures = rootFeatures;
		this.binaryFeatures = binaryFeatures;
	}

	public final transient DenseLexicalFeature lexicalCategoryFeatures;
	public final Collection<BilexicalFeature> dependencyFeatures;
	public final Collection<ArgumentSlotFeature> argumentSlotFeatures;
	public final Collection<UnaryRuleFeature> unaryRuleFeatures;
	public final Collection<PrepositionFeature> prepositionFeatures;
	public final Collection<RootCategoryFeature> rootFeatures;
	public final Collection<BinaryFeature> binaryFeatures;

	/**
	 * Use after de-serializing. Means the supertagger folder can change.
	 * 
	 * @param supertaggerBeam
	 */
	public FeatureSet setSupertaggingFeature(final File model, final double supertaggerBeam) throws IOException {
		return new FeatureSet(new DenseLexicalFeature(model, supertaggerBeam), dependencyFeatures,
				argumentSlotFeatures, unaryRuleFeatures, prepositionFeatures, rootFeatures, binaryFeatures);
	}

	public Collection<Feature> getAllFeatures() {
		final List<Feature> result = new ArrayList<>();
		result.add(lexicalCategoryFeatures);
		result.addAll(argumentSlotFeatures);
		result.addAll(unaryRuleFeatures);
		result.addAll(prepositionFeatures);
		result.addAll(rootFeatures);
		result.addAll(binaryFeatures);
		result.addAll(dependencyFeatures);

		return result;
	}
}