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

	protected final boolean includeDeps;

	public AgendaItem(final SyntaxTreeNode node, final double insideScore, final double outsideScoreUpperbound,
			final int startIndex, final int length, final boolean includeDeps) {
		super();
		this.parse = node;
		this.insideScore = insideScore;
		this.outsideScoreUpperbound = outsideScoreUpperbound;
		this.cost = insideScore + outsideScoreUpperbound;
		this.startOfSpan = startIndex;
		this.spanLength = length;
		this.includeDeps = includeDeps;
		this.key = getEquivalenceClassKey2();
	}

	/**
	 * Comparison function used to order the agenda.
	 */
	@Override
	public int compareTo(final AgendaItem o) {
		return compare(o.getCost(), getCost());
	}

	private int compare(final double d1, final double d2) {
		return d1 < d2 ? -1 : (d1 > d2 ? 1 : 0);
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

	public double getOutsideScoreUpperbound() { return outsideScoreUpperbound; }

	public int getSpanLength() {
		return spanLength;
	}

	private final Object key;

	public Object getEquivalenceClassKey() {
		return key;
	}

	private Object getEquivalenceClassKey2() {

		// Same category
		// Same unused SRL labels
		// Same dependency structure
		// Same rule
		final RuleClass ruleClass = parse.getRuleClass();

		return includeDeps ? new KeyWithDeps(parse.getCategory(), ruleClass, parse.getDependencyStructure(),
				parse.getResolvedUnlabelledDependencies())
		// If not using dependencies, just use the Category as a key. Technically we should include the ruleClass too,
		// but it doesn't affect results, and this way is much faster.
				: parse.getCategory();
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
}