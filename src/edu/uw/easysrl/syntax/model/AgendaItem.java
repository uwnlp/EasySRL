package edu.uw.easysrl.syntax.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleClass;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;

public class AgendaItem implements Comparable<AgendaItem> {
	final SyntaxTreeNode parse;
	private final double insideScore;
	final double outsideScoreUpperbound;
	private final double cost;
	final int startOfSpan;
	final int spanLength;

	private final boolean includeDeps;

	AgendaItem(final SyntaxTreeNode node, final double insideScore, final double outsideScoreUpperbound,
			final int startIndex, final int length, final boolean includeDeps) {
		super();
		this.parse = node;
		this.insideScore = insideScore;
		this.outsideScoreUpperbound = outsideScoreUpperbound;
		this.cost = insideScore + outsideScoreUpperbound;
		this.startOfSpan = startIndex;
		this.spanLength = length;
		this.includeDeps = includeDeps;
	}

	/**
	 * Comparison function used to order the agenda.
	 */
	@Override
	public int compareTo(final AgendaItem o) {
		final int result = Double.compare(o.getCost(), getCost());

		return result;
	}

	public SyntaxTreeNode getParse() {
		return parse;
	}

	public int getStartOfSpan() {
		return startOfSpan;
	}

	public double getCost() {
		return cost;
	}

	public double getInsideScore() {
		return insideScore;
	}

	public int getSpanLength() {
		return spanLength;
	}

	public Object getEquivalenceClassKey() {

		// Same category
		// Same unused SRL labels
		// Same depenency structure
		// Same rule
		final RuleClass ruleClass = parse.getRuleClass();

		return includeDeps ? new KeyWithDeps(parse.getCategory(), ruleClass, parse.getDependencyStructure(),
				parse.getResolvedUnlabelledDependencies()) : new KeyNoDeps(parse.getCategory(), ruleClass);
	}

	private static class KeyWithDeps {
		private final Category category;
		private final RuleClass rule;
		private final DependencyStructure deps;
		private final Set<UnlabelledDependency> unlabelledDependencies;

		public KeyWithDeps(final Category category, final RuleClass ruleClass, final DependencyStructure deps,
				final List<UnlabelledDependency> unlabelledDependencies) {
			super();
			this.category = category.withoutAnnotation();
			this.rule = ruleClass;
			this.deps = deps;
			// Turning into a set to lose ordering.
			this.unlabelledDependencies = new HashSet<>(unlabelledDependencies);
		}

		@Override
		public int hashCode() {
			return Objects.hash(category, deps, rule, unlabelledDependencies);

		}

		@Override
		public boolean equals(final Object obj) {
			final AgendaItem.KeyWithDeps other = (AgendaItem.KeyWithDeps) obj;
			return Objects.equals(category, other.category) && Objects.equals(deps, other.deps)
					&& Objects.equals(rule, other.rule)
					&& Objects.equals(unlabelledDependencies, other.unlabelledDependencies);

		}

	}

	private static class KeyNoDeps {
		private final Category category;
		private final RuleClass rule;

		public KeyNoDeps(final Category category, final RuleClass ruleClass) {
			super();
			this.category = category.withoutAnnotation();
			this.rule = ruleClass;
		}

		@Override
		public int hashCode() {
			return Objects.hash(category, rule);
		}

		@Override
		public boolean equals(final Object obj) {

			final AgendaItem.KeyNoDeps other = (AgendaItem.KeyNoDeps) obj;
			return Objects.equals(category, other.category) && Objects.equals(rule, other.rule);
		}
	}

}