package edu.uw.easysrl.syntax.training;

import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.grammar.NormalForm;
import edu.uw.easysrl.syntax.grammar.SeenRules;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;

/**
 *
 * CKY implementation used to create the training charts. Unlike the normal parsing implementation, this version creates
 * complete packed forests, and doesn't use a model.
 */
class CKY {
	private final Multimap<Category, UnaryRule> unaryRules;
	private final SeenRules seenRules;
	private final NormalForm normalForm;
	private final int maxLength;

	private final int maxChartSize;

	CKY(final File modelFolder, final int maxSentenceLength, final int maxChartSize) throws IOException {
		this.maxLength = maxSentenceLength;
		this.unaryRules = AbstractParser.loadUnaryRules(new File(modelFolder, "unaryRules"));
		this.seenRules = new SeenRules(new File(modelFolder, "seenRules"), TaggerEmbeddings.loadCategories(new File(
				modelFolder, "categories")));
		this.maxChartSize = maxChartSize;
		this.normalForm = new NormalForm();
	}

	ChartCell[][] parse(final List<String> words, final List<Collection<Category>> input) {

		final int numWords = input.size();
		if (input.size() > maxLength) {
			return null;
		}

		final ChartCell[][] chart = new ChartCell[numWords][numWords];

		// Add lexical categories
		for (int i = 0; i < numWords; i++) {
			chart[i][0] = makeChartCell(words, input.get(i), i);
		}

		int size = 0;
		for (int spanLength = 2; spanLength <= numWords; spanLength++) {
			for (int startOfSpan = 0; startOfSpan <= numWords - spanLength; startOfSpan++) {
				final ChartCell newCell = makeChartCell(chart, startOfSpan, spanLength);

				chart[startOfSpan][spanLength - 1] = newCell;
				size += newCell.entries.values().size();

				if (size > maxChartSize) {

					return null;
				}
			}
		}

		// System.out.println("Chart size=" + size);
		return chart;

	}

	private ChartCell makeChartCell(final ChartCell[][] chart, final int startOfSpan, final int spanLength) {

		final Multimap<EquivalenceClassKey, EquivalenceClassValue> entries = HashMultimap.create();
		for (int spanSplit = 1; spanSplit < spanLength; spanSplit++) {
			final ChartCell left = chart[startOfSpan][spanSplit - 1];
			final ChartCell right = chart[startOfSpan + spanSplit][spanLength - spanSplit - 1];

			makeChartCell(entries, left, right, spanLength, chart.length, startOfSpan);
		}
		return ChartCell.make(entries);
	}

	private void makeChartCell(final Multimap<EquivalenceClassKey, EquivalenceClassValue> entries,
			final ChartCell left, final ChartCell right, final int spanLength, final int sentenceLength,
			final int startOfSpan) {

		for (final EquivalenceClassKey l : left.entries.keySet()) {
			for (final EquivalenceClassKey r : right.entries.keySet()) {
				if (l.length() + r.length() != spanLength) {
					throw new RuntimeException("Inconsistent span lengths");
				}
				if (!seenRules.isSeen(l.getCategory(), r.getCategory())) {
					continue;
				}

				for (final RuleProduction rule : Combinator.getRules(l.getCategory(), r.getCategory(),
						Combinator.STANDARD_COMBINATORS)) {

					final RuleType leftRuleClass = l.getRuleType();
					final RuleType ruleType = rule.getRuleType();
					final RuleType rightRuleClass = r.getRuleType();

					if (!normalForm.isOk(leftRuleClass.getNormalFormClassForRule(),
							rightRuleClass.getNormalFormClassForRule(), ruleType, l.getCategory(), r.getCategory(),
							rule.getCategory(), startOfSpan == 0)) {
						continue;
					}

					final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
					final DependencyStructure result = rule.getCombinator().apply(l.dependencyStructure,
							r.dependencyStructure, resolvedDependencies);

					addEntry(entries, EquivalenceClassValue.make(resolvedDependencies, l, r), rule.getCategory(),
							ruleType, l.length + r.length, sentenceLength, result);
				}
			}
		}

	}

	private void addEntry(final Multimap<EquivalenceClassKey, EquivalenceClassValue> nodes,
			final EquivalenceClassValue newNode, final Category category, final RuleType ruleType, final int length,
			final int sentenceLength, final DependencyStructure deps) {
		final EquivalenceClassKey key = new EquivalenceClassKey(category, ruleType, length, deps);

		nodes.put(key, newNode);

		if (length != sentenceLength // && (ruleType != RuleType.LP && ruleType != RuleType.RP)
				) {
			// Don't allow unary rules that span sentence.
			for (final UnaryRule unary : unaryRules.get(category)) {
				final List<UnlabelledDependency> resolvedDependencies = new ArrayList<>();
				final DependencyStructure newDeps = unary.getDependencyStructureTransformation().apply(
						key.dependencyStructure, resolvedDependencies);

				addEntry(nodes, EquivalenceClassValue.make(resolvedDependencies, key), unary.getCategory(), unary
						.getCategory().isForwardTypeRaised() ? RuleType.FORWARD_TYPERAISE : (unary.getCategory()
						.isBackwardTypeRaised() ? RuleType.BACKWARD_TYPE_RAISE : RuleType.TYPE_CHANGE), length,
						sentenceLength, newDeps);
			}
		}

	}

	private ChartCell makeChartCell(final List<String> words, final Collection<Category> categories,
			final int sentencePosition) {

		final Multimap<EquivalenceClassKey, EquivalenceClassValue> nodes = HashMultimap.create();
		for (final Category c : categories) {
			final DependencyStructure deps = DependencyStructure.make(c, words.get(sentencePosition), sentencePosition);
			if (deps == null) {
				throw new RuntimeException("Missing markup for: " + c);
			}
			addEntry(nodes, EquivalenceClassValue.make(Collections.emptyList()), c, RuleType.LEXICON, 1, words.size(),
					deps);
		}

		return ChartCell.make(nodes);

	}

	static class ChartCell {
		private static ChartCell make(final Multimap<EquivalenceClassKey, EquivalenceClassValue> nodes) {
			return new ChartCell(nodes);
		}

		private ChartCell(final Multimap<EquivalenceClassKey, EquivalenceClassValue> nodes) {
			this.entries = nodes;
		}

		private final Multimap<EquivalenceClassKey, EquivalenceClassValue> entries;

		public Multimap<EquivalenceClassKey, EquivalenceClassValue> getEntries() {
			return entries;
		}

		Collection<EquivalenceClassValue> getEntries(final EquivalenceClassKey disjunctiveNode) {
			return new ArrayList<>(entries.asMap().get(disjunctiveNode));
		}

		public Collection<EquivalenceClassKey> getKeys() {
			return entries.keySet();
		}

	}

	static class EquivalenceClassValue implements Serializable {

		private Integer hashCode;

		@Override
		public int hashCode() {
			if (hashCode == null) {

				hashCode = Objects.hashCode(this.children, this.resolvedDependencies);
			}
			return hashCode;
		}

		@Override
		public boolean equals(final Object obj) {
			final EquivalenceClassValue other = (EquivalenceClassValue) obj;
			return children.equals(other.children) && resolvedDependencies.equals(other.resolvedDependencies);
		}

		/**
		 *
		 */
		private static final long serialVersionUID = -4213228451882665997L;
		private final Set<UnlabelledDependency> resolvedDependencies;
		private final List<EquivalenceClassKey> children;

		private EquivalenceClassValue(final List<UnlabelledDependency> resolvedDependencies,
				final EquivalenceClassKey... children) {
			this.resolvedDependencies = ImmutableSet.copyOf(resolvedDependencies);
			this.children = Arrays.asList(children);

		}

		List<EquivalenceClassKey> getChildren() {
			return children;
		}

		private static EquivalenceClassValue make(final List<UnlabelledDependency> resolvedDependencies,
				final EquivalenceClassKey... children) {
			return new EquivalenceClassValue(resolvedDependencies, children);
		}

		public Set<UnlabelledDependency> getResolvedDependencies() {
			return resolvedDependencies;
		}

	}

	static class EquivalenceClassKey implements Serializable{
		/**
		 *
		 */
		private static final long serialVersionUID = -5264190042743497594L;

		private final int length;
		private final RuleType ruleType;
		private final Category category;

		private final DependencyStructure dependencyStructure;

		private EquivalenceClassKey(final Category category, final RuleType ruleType, final int length,
				final DependencyStructure dependencyStructure) {

			this.category = category.withoutAnnotation();
			this.length = length;
			this.ruleType = ruleType;
			this.dependencyStructure = dependencyStructure;
		}

		Category getCategory() {
			return category;
		}

		private Integer hashcode = null;

		@Override
		public int hashCode() {
			if (hashcode == null) {
				hashcode = Objects.hashCode(length, category, ruleType, dependencyStructure);
			}
			return hashcode;
		}

		@Override
		public boolean equals(final Object obj) {

			final EquivalenceClassKey other = (EquivalenceClassKey) obj;
			return length == other.length && category.equals(other.category) && ruleType == other.ruleType
					&& dependencyStructure.equals(other.dependencyStructure);
		}

		int length() {
			return length;

		}

		public RuleType getRuleType() {
			return ruleType;
		}

		public DependencyStructure getDependencyStructure() {
			return dependencyStructure;
		}
	}

	public Multimap<Category, UnaryRule> getUnaryRules() {
		return unaryRules;
	}

}
