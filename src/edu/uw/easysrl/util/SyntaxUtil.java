package edu.uw.easysrl.util;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;

public class SyntaxUtil {
	private SyntaxUtil() {
	}

	public static Stream<SyntaxTreeNode> subtreeStream(final SyntaxTreeNode node) {
		return Stream.concat(Stream.of(node), node.getChildren().stream().flatMap(SyntaxUtil::subtreeStream));
	}

	// Does not check whether parses come from the same sentence.
	public static boolean parsesEqual(final SyntaxTreeNode parse1, final SyntaxTreeNode parse2) {
		return parse1.getStartIndex() == parse2.getStartIndex()
				&& parse1.getEndIndex() == parse2.getEndIndex()
				&& parse1.getCategory() == parse2.getCategory()
				&& parse1.getRuleType().ordinal() == parse2.getRuleType().ordinal()
				&& parse1.getChildren().size() == parse2.getChildren().size()
				&& Util.zip(parse1.getChildren().stream(), parse2.getChildren().stream(), SyntaxUtil::parsesEqual)
				.allMatch(Boolean::booleanValue);
	}

	// Does not encode word information.
	public static int parseHash(final SyntaxTreeNode parse) {
		int stepHash = Objects
				.hash(parse.getStartIndex(), parse.getEndIndex(), parse.getCategory(), parse.getRuleType().ordinal());
		int childrenHash = Arrays.hashCode(parse.getChildren().stream().mapToInt(SyntaxUtil::parseHash).toArray());
		return Objects.hash(stepHash, childrenHash);
	}
}
