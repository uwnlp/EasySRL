package edu.uw.easysrl.syntax.parser;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import com.google.common.collect.Ordering;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.syntax.parser.ChartCell.CellNoDynamicProgram;

public class ParserBeamSearch extends ParserCKY {

	public ParserBeamSearch(final ModelFactory modelFactory, final int maxSentenceLength, final int nbest,
			final List<Category> validRootCategories, final File modelFolder, final int maxChartSize)
			throws IOException {
		super(modelFactory, maxSentenceLength, nbest, validRootCategories, modelFolder, maxChartSize);
	}

	private final Comparator<AgendaItem> orderByInsideScore = new Comparator<AgendaItem>() {

		@Override
		public int compare(final AgendaItem o1, final AgendaItem o2) {
			return Double.compare(o1.getInsideScore(), o2.getInsideScore());
		}
	};

	@Override
	ChartCell makeChartCell(final ChartCell[][] chart, final int startOfSpan, final int spanLength, final Model model) {

		final ChartCell cell = super.makeChartCell(chart, startOfSpan, spanLength, model);
		final List<AgendaItem> best = Ordering.from(orderByInsideScore).greatestOf(cell.getEntries(), nbest);
		return new CellNoDynamicProgram(best);
	}

	@Override
	ChartCell createCell() {
		return new CellNoDynamicProgram();
	}
}
