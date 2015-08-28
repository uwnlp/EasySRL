package edu.uw.easysrl.syntax.grammar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.DependencyStructure.ResolvedDependency;
import edu.uw.easysrl.dependencies.DependencyStructure.UnlabelledDependency;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;

public abstract class SyntaxTreeNode implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private final Category category;
	private final int headIndex;
	private final DependencyStructure dependencyStructure;
	private final List<UnlabelledDependency> resolvedUnlabelledDependencies;
	private final int length;

	public abstract SyntaxTreeNodeLeaf getHead();

	private SyntaxTreeNode(final Category category, final int headIndex,
			final DependencyStructure dependencyStructure,
			final List<UnlabelledDependency> resolvedUnlabelledDependencies,
			final int length) {
		this.category = category;
		this.headIndex = headIndex;
		this.dependencyStructure = dependencyStructure;
		this.resolvedUnlabelledDependencies = resolvedUnlabelledDependencies;
		this.length = length;
	}

	public static class SyntaxTreeNodeBinary extends SyntaxTreeNode {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final RuleType ruleType;
		private final boolean headIsLeft;
		private final SyntaxTreeNode leftChild;
		private final SyntaxTreeNode rightChild;

		public SyntaxTreeNodeBinary(final Category category,
				final SyntaxTreeNode leftChild,
				final SyntaxTreeNode rightChild, final RuleType ruleType,
				final boolean headIsLeft,
				final DependencyStructure dependencyStructure,
				final List<UnlabelledDependency> resolvedUnlabelledDependencies) {
			super(category, headIsLeft ? leftChild.getHeadIndex() : rightChild
					.getHeadIndex(), dependencyStructure,
					resolvedUnlabelledDependencies, leftChild.length
							+ rightChild.length);
			this.ruleType = ruleType;
			this.headIsLeft = headIsLeft;
			this.leftChild = leftChild;
			this.rightChild = rightChild;
		}

		@Override
		public void accept(final SyntaxTreeNodeVisitor v) {
			v.visit(this);
		}

		@Override
		public RuleType getRuleType() {
			return ruleType;
		}

		@Override
		public List<SyntaxTreeNode> getChildren() {
			return Arrays.asList(getLeftChild(), getRightChild());
		}

		@Override
		public SyntaxTreeNodeLeaf getHead() {
			return isHeadIsLeft() ? getLeftChild().getHead() : getRightChild()
					.getHead();
		}

		@Override
		public SyntaxTreeNode getChild(final int index) {
			if (index == 0) {
				return getLeftChild();
			}
			if (index == 1) {
				return getRightChild();
			}
			throw new RuntimeException("Binary Node does not have child: "
					+ index);
		}

		@Override
		public boolean getHeadIsLeft() {
			return isHeadIsLeft();
		}

		@Override
		public Collection<ResolvedDependency> getDependenciesLabelledAtThisNode() {
			return Collections.emptyList();

		}

		public SyntaxTreeNode getLeftChild() {
			return leftChild;
		}

		public SyntaxTreeNode getRightChild() {
			return rightChild;
		}

		public boolean isHeadIsLeft() {
			return headIsLeft;
		}

	}

	public static class SyntaxTreeNodeLeaf extends SyntaxTreeNode {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		public SyntaxTreeNodeLeaf(final String word, final String pos,
				final String ner, final Category category,
				final int sentencePosition) {
			super(category, sentencePosition, DependencyStructure.make(
					category, word, sentencePosition), Collections.emptyList(),
					1);
			this.pos = pos;
			this.ner = ner;
			this.word = word;
		}

		private final String pos;
		private final String ner;
		private final String word;

		@Override
		public void accept(final SyntaxTreeNodeVisitor v) {
			v.visit(this);
		}

		@Override
		public RuleType getRuleType() {
			return RuleType.LEXICON;
		}

		@Override
		public List<SyntaxTreeNode> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public boolean isLeaf() {
			return true;
		}

		@Override
		void getWords(final List<SyntaxTreeNodeLeaf> result) {
			result.add(this);
		}

		public String getPos() {
			return pos;
		}

		public String getNER() {
			return ner;
		}

		public String getWord() {
			return word;
		}

		@Override
		public SyntaxTreeNodeLeaf getHead() {
			return this;
		}

		public int getSentencePosition() {
			return getHeadIndex();
		}

		@Override
		public SyntaxTreeNode getChild(final int index) {

			throw new RuntimeException("Leaf node does not have child: "
					+ index);

		}

		@Override
		public boolean getHeadIsLeft() {
			return false;
		}

		@Override
		public Collection<ResolvedDependency> getDependenciesLabelledAtThisNode() {
			return Collections.emptyList();

		}
	}

	public static class SyntaxTreeNodeUnary extends SyntaxTreeNode {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final UnaryRule unaryRule;
		private final RuleType ruleType;

		public SyntaxTreeNodeUnary(final Category category,
				final SyntaxTreeNode child,
				final DependencyStructure dependencyStructure,
				final UnaryRule unaryRule,
				final List<UnlabelledDependency> resolvedUnlabelledDependencies) {
			super(category, child.getHeadIndex(), dependencyStructure,
					resolvedUnlabelledDependencies, child.length);

			this.child = child;
			this.unaryRule = unaryRule;
			this.ruleType = category.isForwardTypeRaised() ? RuleType.FORWARD_TYPERAISE
					: (category.isBackwardTypeRaised() ? RuleType.BACKWARD_TYPE_RAISE
							: RuleType.TYPE_CHANGE);
		}

		private final SyntaxTreeNode child;

		@Override
		public void accept(final SyntaxTreeNodeVisitor v) {
			v.visit(this);
		}

		@Override
		public RuleType getRuleType() {
			return ruleType;
		}

		@Override
		public List<SyntaxTreeNode> getChildren() {
			return Arrays.asList(getChild());
		}

		@Override
		public SyntaxTreeNodeLeaf getHead() {
			return getChild().getHead();
		}

		@Override
		public SyntaxTreeNode getChild(final int index) {
			if (index == 0) {
				return child;
			}
			throw new RuntimeException("Unary node does not have child: "
					+ index);
		}

		@Override
		public boolean getHeadIsLeft() {
			return true;
		}

		public UnaryRule getUnaryRule() {
			return unaryRule;
		}

		@Override
		public Collection<ResolvedDependency> getDependenciesLabelledAtThisNode() {
			return Collections.emptyList();
		}

		public SyntaxTreeNode getChild() {
			return child;
		}
	}

	@Override
	public String toString() {
		return ParsePrinter.CCGBANK_PRINTER.print(this, -1);
	}

	public int getHeadIndex() {
		return headIndex;
	}

	public abstract void accept(SyntaxTreeNodeVisitor v);

	public interface SyntaxTreeNodeVisitor {
		void visit(SyntaxTreeNodeBinary node);

		void visit(SyntaxTreeNodeUnary node);

		void visit(SyntaxTreeNodeLeaf node);

		void visit(SyntaxTreeNodeLabelling syntaxTreeNodeLabelling);
	}

	public abstract RuleType getRuleType();

	public abstract List<SyntaxTreeNode> getChildren();

	public boolean isLeaf() {
		return false;
	}

	private List<SyntaxTreeNodeLeaf> words = null;

	/**
	 * Returns all the terminal nodes in the sentence, from left to right.
	 */
	public List<SyntaxTreeNodeLeaf> getWords() {
		if (words == null) {
			final List<SyntaxTreeNodeLeaf> result = new ArrayList<>();
			getWords(result);
			words = result;
		}

		return words;
	}

	void getWords(final List<SyntaxTreeNodeLeaf> result) {
		for (final SyntaxTreeNode child : getChildren()) {
			child.getWords(result);
		}
	}

	public Category getCategory() {
		return category;
	}

	public DependencyStructure getDependencyStructure() {
		return dependencyStructure;
	}

	public abstract SyntaxTreeNode getChild(int index);

	public abstract boolean getHeadIsLeft();

	public abstract Collection<ResolvedDependency> getDependenciesLabelledAtThisNode();

	public final List<UnlabelledDependency> getResolvedUnlabelledDependencies() {
		return resolvedUnlabelledDependencies;
	}

	public static class SyntaxTreeNodeLabelling extends SyntaxTreeNode {

		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final Collection<ResolvedDependency> labelled;
		private final SyntaxTreeNode child;

		public SyntaxTreeNodeLabelling(final SyntaxTreeNode child,
				final Collection<ResolvedDependency> labelled,
				final List<UnlabelledDependency> unlabelled) {
			super(child.category, child.getHeadIndex(), child
					.getDependencyStructure(), unlabelled, child.length);
			this.labelled = labelled;
			this.child = child;
		}

		@Override
		public SyntaxTreeNodeLeaf getHead() {
			return child.getHead();
		}

		@Override
		public void accept(final SyntaxTreeNodeVisitor v) {
			v.visit(this);
		}

		@Override
		public RuleType getRuleType() {
			return child.getRuleType();
		}

		@Override
		public List<SyntaxTreeNode> getChildren() {
			return Arrays.asList(child);
		}

		@Override
		public SyntaxTreeNode getChild(final int index) {
			Preconditions.checkArgument(index == 0);
			return child;
		}

		@Override
		public boolean getHeadIsLeft() {
			return true;
		}

		@Override
		public Collection<ResolvedDependency> getDependenciesLabelledAtThisNode() {
			return labelled;
		}

	}

	public List<ResolvedDependency> getAllLabelledDependencies() {
		final List<ResolvedDependency> result = new ArrayList<>();
		getAllLabelledDependencies(result);
		return result;
	}

	private void getAllLabelledDependencies(
			final List<ResolvedDependency> result) {
		result.addAll(getDependenciesLabelledAtThisNode());
		for (final SyntaxTreeNode child : getChildren()) {
			child.getAllLabelledDependencies(result);
		}
	}

	public int getLength() {
		return length;
	}

}