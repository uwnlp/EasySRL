package edu.uw.easysrl.dependencies;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLabelling;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeVisitor;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;

/**
 * Takes parses that don't have explicit dependency information, and adds it.
 *
 */
public class DependencyGenerator implements Serializable {
	private static final long serialVersionUID = 1L;
	private final Multimap<Category, UnaryRule> unaryRules;

	public DependencyGenerator(final File modelFolder) throws IOException {
		this(AbstractParser.loadUnaryRules(new File(modelFolder, "unaryRules")));
		Coindexation.parseMarkedUpFile(new File(modelFolder, "markedup"));
	}

	public DependencyGenerator(final Multimap<Category, UnaryRule> unaryRules) throws IOException {
		this.unaryRules = unaryRules;
	}

	public SyntaxTreeNode generateDependencies(final SyntaxTreeNode raw, final Collection<UnlabelledDependency> deps) {
		final AddDependenciesVisitor visitor = new AddDependenciesVisitor(deps);
		raw.accept(visitor);
		final SyntaxTreeNode result = visitor.stack.pop();
		Preconditions.checkState(visitor.stack.size() == 0);
		return result;
	}

	private class AddDependenciesVisitor implements SyntaxTreeNodeVisitor {
		private final Stack<SyntaxTreeNode> stack = new Stack<>();
		private final Collection<UnlabelledDependency> deps;

		public AddDependenciesVisitor(final Collection<UnlabelledDependency> deps) {
			this.deps = deps;
		}

		@Override
		public void visit(final SyntaxTreeNodeLabelling node) {
			node.getChild(0).accept(this);
			stack.push(new SyntaxTreeNodeLabelling(stack.pop(), node.getAllLabelledDependencies(), node
					.getResolvedUnlabelledDependencies()));
		}

		@Override
		public void visit(final SyntaxTreeNodeLeaf node) {
			stack.push(new SyntaxTreeNodeLeaf(node.getWord(), node.getPos(), node.getNER(), node.getCategory(), node
					.getSentencePosition()));
		}

		@Override
		public void visit(final SyntaxTreeNodeUnary node) {
			node.getChild(0).accept(this);
			final SyntaxTreeNode child = stack.pop();
			for (final UnaryRule unary : unaryRules.get(child.getCategory())) {
				if (unary.getCategory().equals(node.getCategory())) {
					final List<UnlabelledDependency> resolvedDeps = new ArrayList<>();
					final DependencyStructure childDeps = child.getDependencyStructure();
					final DependencyStructure newDeps = unary.getDependencyStructureTransformation().apply(childDeps,
							resolvedDeps);

					deps.addAll(resolvedDeps);
					stack.push(new SyntaxTreeNodeUnary(node.getCategory(), child, newDeps, unary, resolvedDeps));
					return;
				}
			}

			throw new IllegalStateException("Didn't find matching unary rule");
		}

		@Override
		public void visit(final SyntaxTreeNodeBinary node) {
			node.getChild(0).accept(this);
			final SyntaxTreeNode left = stack.pop();
			node.getChild(1).accept(this);
			final SyntaxTreeNode right = stack.pop();
			final Collection<RuleProduction> rules = Combinator.getRules(left.getCategory(), right.getCategory(),
					Combinator.STANDARD_COMBINATORS);
			for (final RuleProduction rule : rules) {
				if (rule.getCategory().equals(node.getCategory())) {
					final List<UnlabelledDependency> resolvedDeps = new ArrayList<>();
					final DependencyStructure newDeps = rule.getCombinator().apply(left.getDependencyStructure(),
							right.getDependencyStructure(), resolvedDeps);
					final SyntaxTreeNode result = new SyntaxTreeNodeBinary(node.getCategory(), left, right,
							rule.getRuleType(), rule.isHeadIsLeft(), newDeps, resolvedDeps);
					deps.addAll(resolvedDeps);

					stack.push(result);
					return;
				}
			}
			throw new IllegalStateException("Didn't find matching binary rule: " + left.getCategory() + " + "
					+ right.getCategory() + " --> " + node.getCategory());

		}
	};

}
