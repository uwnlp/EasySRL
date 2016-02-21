package edu.uw.easysrl.util;

import com.google.common.base.Preconditions;

import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.tagger.Tagger;

public class BatchUtil {
	private BatchUtil() {
	}

	private static InputToParser toParserInput(List<InputWord> words,
			List<List<Tagger.ScoredCategory>> tagDistribution) {
		Preconditions.checkState(words.size() == tagDistribution.size());
		return new InputToParser(words, null, tagDistribution, true);
	}

	public static Stream<List<Util.Scored<SyntaxTreeNode>>> parseBatched(Supplier<Stream<List<InputWord>>> sentences,
			Tagger tagger, Parser parser) {
		return zip(sentences.get(), tagger.tagBatch(sentences.get()), BatchUtil::toParserInput).map(parser::doParsing);
	}

	// zip(Stream.of("a", "b", "c"), Stream.of("1", "2", "3"), (x,y) -> x + y)
	// -> Stream.of("a1", "b2", "c3")
	private static <A, B, C> Stream<C> zip(Stream<? extends A> a, Stream<? extends B> b,
			BiFunction<? super A, ? super B, ? extends C> zipper) {
		final Iterator<? extends A> iteratorA = a.iterator();
		final Iterator<? extends B> iteratorB = b.iterator();
		final Iterable<C> iterable = () -> new Iterator<C>() {
			@Override
			public boolean hasNext() {
				return iteratorA.hasNext() && iteratorB.hasNext();
			}

			@Override
			public C next() {
				return zipper.apply(iteratorA.next(), iteratorB.next());
			}
		};
		return StreamSupport.stream(iterable.spliterator(), a.isParallel() || b.isParallel());
	}
}
