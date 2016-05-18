package edu.uw.easysrl.syntax.parser;

import com.google.common.collect.ListMultimap;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.uw.easysrl.dependencies.Coindexation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.NormalForm;
import edu.uw.easysrl.syntax.grammar.SeenRules;
import edu.uw.easysrl.syntax.model.CutoffsDictionaryInterface;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.syntax.model.SRLFactoredModel.SRLFactoredModelFactory;
import edu.uw.easysrl.syntax.model.SupertagFactoredModel.SupertagFactoredModelFactory;
import edu.uw.easysrl.syntax.model.feature.Feature.FeatureKey;
import edu.uw.easysrl.syntax.model.feature.FeatureSet;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.syntax.training.Training;
import edu.uw.easysrl.util.Util;

public abstract class ParserBuilder<T extends ParserBuilder<T>> {

	ParserBuilder(final File modelFolder) {
		modelFolder(modelFolder);
	}

	ParserBuilder() { }

	private File modelFolder;

	public File getModelFolder() {
		return modelFolder;
	}

	public Collection<Category> getLexicalCategories() {
		return lexicalCategories;
	}

	public int getMaxSentenceLength() {
		return maxSentenceLength;
	}

	public int getNbest() {
		return nbest;
	}

	public List<Category> getValidRootCategories() {
		return validRootCategories;
	}

	public ListMultimap<Category, UnaryRule> getUnaryRules() {
		return unaryRules;
	}

	public File getMarkedupFile() {
		return markedupFile;
	}

	public SeenRules getSeenRules() {
		return seenRules;
	}

	public boolean getAllowUnseenRules() {
		return allowUnseenRules;
	}

	public double getSupertaggerBeam() {
		return supertaggerBeam;
	}

	public Boolean getJointModel() {
		return jointModel;
	}

	public Double getSupertaggerWeight() {
		return supertaggerWeight;
	}

	public Tagger getTagger() {
		return tagger;
	}

	public ModelFactory getModelFactory() {
		return modelFactory;
	}

	public CutoffsDictionaryInterface getCutoffs() {
		return cutoffs;
	}

	public boolean isUseSupertaggedInput() {
		return useSupertaggedInput;
	}

	public List<Combinator> getCombinators() {
		return combinators;
	}

	public int getMaxChartSize() {
		return maxChartSize;
	}

	public int getMaxAgendaSize() {
		return maxAgendaSize;
	}

	public NormalForm getNormalForm() {
		return normalForm;
	}

	public double getNbestBeam() {
		return nbestBeam;
	}

	public List<ParserListener> getListeners() {
		return listeners;
	}

	private Collection<Category> lexicalCategories;
	private int maxSentenceLength = 70;
	private int nbest = 1;
	private List<Category> validRootCategories = Training.ROOT_CATEGORIES;
	private ListMultimap<Category, UnaryRule> unaryRules;
	private File markedupFile;
	private SeenRules seenRules;
	private boolean allowUnseenRules = false;
	private double supertaggerBeam = 0.00001;
	private Boolean jointModel;
	private Double supertaggerWeight;
	private Tagger tagger;
	private ModelFactory modelFactory;
	private CutoffsDictionaryInterface cutoffs;
	private boolean useSupertaggedInput = false;
	private final List<Combinator> combinators = new ArrayList<>(Combinator.STANDARD_COMBINATORS);
	private int maxChartSize = 300000;
	private int maxAgendaSize = Integer.MAX_VALUE;
	private NormalForm normalForm = new NormalForm();
	private double nbestBeam = 0.001;
	private List<ParserListener> listeners = Collections.emptyList();

	public T nBest(final int nBest) {
		this.nbest = nBest;
		return getThis();
	}

	public T maximumSentenceLength(final int maxSentenceLength) {
		this.maxSentenceLength = maxSentenceLength;
		return getThis();
	}

	public T validRootCategories(final List<Category> validRootCategories) {
		this.validRootCategories = validRootCategories;
		return getThis();
	}

	public T seenRules(final SeenRules seenRules) {
		this.seenRules = seenRules;
		return getThis();
	}

	public T allowUnseenRules(final boolean allowUnseenRules) {
		this.allowUnseenRules = allowUnseenRules;
		return getThis();
	}

	public T pipelineModel() {
		this.jointModel = false;
		return getThis();
	}

	public T supertaggerBeam(final double supertaggerBeam) {
		this.supertaggerBeam = supertaggerBeam;
		return getThis();
	}

	public T useSupertaggedInput() {
		this.useSupertaggedInput = true;
		return getThis();
	}

	public T tagger(final Tagger tagger) {
		this.tagger = tagger;
		return getThis();
	}

	public T normalForm(final NormalForm normalForm) {
		this.normalForm = normalForm;
		return getThis();
	}

	public T modelFactory(final ModelFactory modelFactory) {
		this.modelFactory = modelFactory;
		return getThis();
	}

	public T nbestBeam(final double nbestBeam) {
		this.nbestBeam = nbestBeam;
		return getThis();
	}

	public T modelFolder(final File modelFolder) {
		this.modelFolder = modelFolder;
		this.jointModel = new File(modelFolder, "weights").exists();
		try {
			this.unaryRules = AbstractParser.loadUnaryRules(new File(modelFolder, "unaryRules"));
			this.lexicalCategories = TaggerEmbeddings.loadCategories(new File(modelFolder, "categories"));
			this.seenRules = new SeenRules(new File(modelFolder, "seenRules"), lexicalCategories);
			final File cutoffsFile = new File(modelFolder, "cutoffs");
			cutoffs = cutoffsFile.exists() ? Util.deserialize(cutoffsFile) : null;
			if (new File(modelFolder, "markedup").exists()) {
				Coindexation.parseMarkedUpFile(new File(modelFolder, "markedup"));
			}
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
		return getThis();
	}

	public T listeners(final List<ParserListener> listeners) {
		this.listeners = listeners;
		return getThis();
	}

	@SuppressWarnings("unchecked")
	T getThis() {
		return (T) this;
	}

	public AbstractParser build() {
		try {
			if (modelFactory == null) {
				if (jointModel) {
					final Map<FeatureKey, Integer> keyToIndex = Util.deserialize(new File(modelFolder, "featureToIndex"));
					final double[] weights = Util.deserialize(new File(modelFolder, "weights"));
					if (supertaggerWeight != null) {
						weights[0] = supertaggerWeight;
					}

					modelFactory = new SRLFactoredModelFactory(weights,
							Util.<FeatureSet>deserialize(new File(modelFolder, "features"))
									.setSupertaggingFeature(new File(modelFolder, "/pipeline"), supertaggerBeam),
							lexicalCategories, cutoffs, keyToIndex);

				} else {
					final Tagger tagger = !useSupertaggedInput ?
							Tagger.make(modelFolder, supertaggerBeam, 50, cutoffs) :
							null;

					modelFactory = new SupertagFactoredModelFactory(tagger, lexicalCategories, nbest > 1);

				}
			}
			return build2();
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public T supertaggerWeight(final Double supertaggerWeight) {
		this.supertaggerWeight = supertaggerWeight;
		return getThis();
	}

	public T maxChartSize(final int maxChartSize) {
		this.maxChartSize = maxChartSize;
		return getThis();
	}

	public T maxAgendaSize(final int maxAgendaSize) {
		this.maxAgendaSize = maxAgendaSize;
		return getThis();
	}

	protected abstract AbstractParser build2();
}
