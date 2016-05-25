package edu.uw.easysrl.syntax.parser;

import java.io.File;
import java.io.IOException;
import java.util.List;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.syntax.parser.ChartCell.CellNoDynamicProgram;

public class ParserBeamSearch extends ParserCKY {

	@Deprecated
	public ParserBeamSearch(final ModelFactory modelFactory, final int maxSentenceLength, final int nbest,
			final List<Category> validRootCategories, final File modelFolder, final int maxChartSize)
			throws IOException {
		super(modelFactory, maxSentenceLength, nbest, validRootCategories, modelFolder, maxChartSize);
	}

	ParserBeamSearch(final Builder builder) {
		super(builder);
	}

	@Override
	ChartCell createCell() {
		return new CellNoDynamicProgram(nbest);
	}

	public static class Builder extends ParserCKY.Builder {

		public Builder(final File modelFolder, final int beamSize) {
			super(modelFolder);
			super.nBest(beamSize);
		}

		@Override
		protected ParserBeamSearch build2() {
			return new ParserBeamSearch(this);
		}
	}
}
