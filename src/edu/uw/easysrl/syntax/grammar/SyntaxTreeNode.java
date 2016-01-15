package edu.uw.easysrl.syntax.grammar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import edu.uw.easysrl.dependencies.DependencyStructure;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.semantics.Logic;
import edu.uw.easysrl.semantics.lexicon.Lexicon;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleClass;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleType;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;

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
	private final Logic semantics;

	public abstract SyntaxTreeNodeLeaf getHead();

	private SyntaxTreeNode(final Category category, final int headIndex, final DependencyStructure dependencyStructure,
			final List<UnlabelledDependency> resolvedUnlabelledDependencies, final int length,
			final Optional<Logic> semantics) {
		this.category = category;
		this.headIndex = headIndex;
		this.dependencyStructure = dependencyStructure;
		// Because subList isn't serializable.
		this.resolvedUnlabelledDependencies = resolvedUnlabelledDependencies == null ? null : ImmutableList
				.copyOf(resolvedUnlabelledDependencies);
		this.length = length;
		this.semantics = semantics.orElse(null);
	}

	public boolean hasDependencies() {
		return dependencyStructure != null;
	}

	public abstract SyntaxTreeNode addSemantics(Lexicon lexicon, CCGandSRLparse parse2);

	public abstract String getWord();

	public static class SyntaxTreeNodeBinary extends SyntaxTreeNode {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final RuleType ruleType;
		private final boolean headIsLeft;
		private final SyntaxTreeNode leftChild;
		private final SyntaxTreeNode rightChild;

		public SyntaxTreeNodeBinary(final Category category, final SyntaxTreeNode leftChild,
				final SyntaxTreeNode rightChild, final RuleType ruleType, final boolean headIsLeft,
				final DependencyStructure dependencyStructure,
				final List<UnlabelledDependency> resolvedUnlabelledDependencies) {
			this(category, leftChild, rightChild, ruleType, headIsLeft, dependencyStructure,
					resolvedUnlabelledDependencies, Optional.empty());
		}

		private SyntaxTreeNodeBinary(final Category category, final SyntaxTreeNode leftChild,
				final SyntaxTreeNode rightChild, final RuleType ruleType, final boolean headIsLeft,
				final DependencyStructure dependencyStructure,
				final List<UnlabelledDependency> resolvedUnlabelledDependencies, final Optional<Logic> semantics) {
			super(category, headIsLeft ? leftChild.getHeadIndex() : rightChild.getHeadIndex(), dependencyStructure,
					resolvedUnlabelledDependencies, leftChild.length + rightChild.length, semantics);
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
			return isHeadIsLeft() ? getLeftChild().getHead() : getRightChild().getHead();
		}

		@Override
		public SyntaxTreeNode getChild(final int index) {
			if (index == 0) {
				return getLeftChild();
			}
			if (index == 1) {
				return getRightChild();
			}
			throw new RuntimeException("Binary Node does not have child: " + index);
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

		@Override
		public SyntaxTreeNode addSemantics(final Lexicon lexicon, final CCGandSRLparse semanticDependencies) {
			Logic semantics;
			final SyntaxTreeNode newLeft;
			final SyntaxTreeNode newRight;
			if (lexicon.isMultiWordExpression(this)) {
				newLeft = leftChild;
				newRight = rightChild;
				semantics = lexicon.getEntry(getWord(), "MW", super.category,
						super.dependencyStructure.getCoindexation());
			} else {
				newLeft = leftChild.addSemantics(lexicon, semanticDependencies);
				newRight = rightChild.addSemantics(lexicon, semanticDependencies);
				semantics = Combinator.fromRule(ruleType).apply(newLeft.semantics, newRight.semantics);

			}
			return new SyntaxTreeNodeBinary(super.category, newLeft, newRight, ruleType, headIsLeft,
					super.dependencyStructure, super.resolvedUnlabelledDependencies, Optional.of(semantics));
		}

		@Override
		public RuleClass getRuleClass() {
			if (ruleType == RuleType.FA && rightChild.getCategory().equals(super.category)) {
				// X/X X --> X
				return RuleClass.F_MOD;
			} else if ((ruleType == RuleType.BA) && leftChild.getCategory().equals(super.category)) {
				// X X\X --> X
				return RuleClass.B_MOD;
			} else {
				return ruleType.getNormalFormClassForRule();
			}
		}

		@Override
		public String getWord() {
			return leftChild.getWord() + " " + rightChild.getWord();
		}

	}

	public static class SyntaxTreeNodeLeaf extends SyntaxTreeNode {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		public SyntaxTreeNodeLeaf(final String word, final String pos, final String ner, final Category category,
				final int sentencePosition) {
			this(word, pos, ner, category, sentencePosition, true);
		}

		public SyntaxTreeNodeLeaf(final String word, final String pos, final String ner, final Category category,
				final int sentencePosition, final boolean includeDependencies) {
			this(word, pos, ner, category, sentencePosition, Optional.empty(), includeDependencies);
		}

		private SyntaxTreeNodeLeaf(final String word, final String pos, final String ner, final Category category,
				final int sentencePosition, final Optional<Logic> semantics, final boolean includeDependencies) {
			super(category, sentencePosition, includeDependencies ? DependencyStructure.make(category, word,
					sentencePosition) : null, Collections.emptyList(), 1, semantics);
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
		void getLeaves(final List<SyntaxTreeNodeLeaf> result) {
			result.add(this);
		}

		public String getPos() {
			return pos;
		}

		public String getNER() {
			return ner;
		}

		@Override
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

			throw new RuntimeException("Leaf node does not have child: " + index);

		}

		@Override
		public boolean getHeadIsLeft() {
			return false;
		}

		@Override
		public Collection<ResolvedDependency> getDependenciesLabelledAtThisNode() {
			return Collections.emptyList();

		}

		@Override
		public SyntaxTreeNode addSemantics(final Lexicon lexicon, final CCGandSRLparse semanticDependencies) {

			final Logic semantics = lexicon.getEntry(semanticDependencies, super.headIndex);
			return new SyntaxTreeNodeLeaf(word, pos, ner, super.category, super.headIndex, Optional.of(semantics),
					hasDependencies());
		}

		@Override
		public RuleClass getRuleClass() {
			return RuleClass.LEXICON;
		}
	}

	public static class SyntaxTreeNodeUnary extends SyntaxTreeNode {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final UnaryRule unaryRule;
		private final RuleType ruleType;

		public SyntaxTreeNodeUnary(final Category category, final SyntaxTreeNode child,
				final DependencyStructure dependencyStructure, final UnaryRule unaryRule,
				final List<UnlabelledDependency> resolvedUnlabelledDependencies) {
			this(category, child, dependencyStructure, unaryRule, resolvedUnlabelledDependencies, Optional.empty());
		}

		private SyntaxTreeNodeUnary(final Category category, final SyntaxTreeNode child,
				final DependencyStructure dependencyStructure, final UnaryRule unaryRule,
				final List<UnlabelledDependency> resolvedUnlabelledDependencies, final Optional<Logic> semantics) {
			super(category, child.getHeadIndex(), dependencyStructure, resolvedUnlabelledDependencies, child.length,
					semantics);

			this.child = child;
			this.unaryRule = unaryRule;
			this.ruleType = category.isForwardTypeRaised() ? RuleType.FORWARD_TYPERAISE : (category
					.isBackwardTypeRaised() ? RuleType.BACKWARD_TYPE_RAISE : RuleType.TYPE_CHANGE);
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
			throw new RuntimeException("Unary node does not have child: " + index);
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

		@Override
		public SyntaxTreeNode addSemantics(final Lexicon lexicon, final CCGandSRLparse semanticDependencies) {
			final SyntaxTreeNode newChild;
			final Logic semantics;
			if (lexicon.isMultiWordExpression(this) && !unaryRule.isTypeRaising()) {
				// Collapse this MWE. Apply type-raising after building semantics for an entity.
				newChild = child;
				semantics = lexicon.getEntry(getWord(), "MW", super.category,
						super.dependencyStructure.getCoindexation());
			} else {
				newChild = child.addSemantics(lexicon, semanticDependencies);
				semantics = unaryRule.apply(newChild.semantics);
			}

			return new SyntaxTreeNodeUnary(super.category, newChild, super.dependencyStructure, unaryRule,
					super.resolvedUnlabelledDependencies, Optional.of(semantics));
		}

		@Override
		public RuleClass getRuleClass() {
			return ruleType.getNormalFormClassForRule();
		}

		@Override
		public String getWord() {
			return child.getWord();
		}
	}

	@Override
	public String toString() {
		return ParsePrinter.CCGBANK_PRINTER.print(this);
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

	public abstract RuleClass getRuleClass();

	public abstract List<SyntaxTreeNode> getChildren();

	public boolean isLeaf() {
		return false;
	}

	private List<SyntaxTreeNodeLeaf> leaves = null;

	/**
	 * Returns all the terminal nodes in the sentence, from left to right.
	 */
	public List<SyntaxTreeNodeLeaf> getLeaves() {
		if (leaves == null) {
			final List<SyntaxTreeNodeLeaf> result = new ArrayList<>();
			getLeaves(result);
			leaves = result;
		}

		return leaves;
	}

	public int getStartIndex() {
		return getLeaves().get(0).getSentencePosition();
	}

	public int getEndIndex() {
		return 1 + getLeaves().get(getLeaves().size() - 1).getSentencePosition();
	}

	void getLeaves(final List<SyntaxTreeNodeLeaf> result) {
		for (final SyntaxTreeNode child : getChildren()) {
			child.getLeaves(result);
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

		public SyntaxTreeNodeLabelling(final SyntaxTreeNode child, final Collection<ResolvedDependency> labelled,
				final List<UnlabelledDependency> unlabelled) {
			super(child.category, child.getHeadIndex(), child.getDependencyStructure(), unlabelled, child.length, child
					.getSemantics());
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

		@Override
		public SyntaxTreeNode addSemantics(final Lexicon lexicon, final CCGandSRLparse semanticDependencies) {
			final SyntaxTreeNode newChild = child.addSemantics(lexicon, semanticDependencies);
			return new SyntaxTreeNodeLabelling(newChild, labelled, super.resolvedUnlabelledDependencies);
		}

		@Override
		public RuleClass getRuleClass() {
			return child.getRuleClass();
		}

		@Override
		public String getWord() {
			return child.getWord();
		}

	}

	public List<ResolvedDependency> getAllLabelledDependencies() {
		final List<ResolvedDependency> result = new ArrayList<>();
		getAllLabelledDependencies(result);
		return result;
	}

	private void getAllLabelledDependencies(final List<ResolvedDependency> result) {
		result.addAll(getDependenciesLabelledAtThisNode());
		for (final SyntaxTreeNode child : getChildren()) {
			child.getAllLabelledDependencies(result);
		}
	}

	public int getLength() {
		return length;
	}

	public Optional<Logic> getSemantics() {
		return Optional.ofNullable(semantics);
	}

}