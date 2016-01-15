package edu.uw.easysrl.main;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.semantics.lexicon.CompositeLexicon;
import edu.uw.easysrl.syntax.parser.SRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.BackoffSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.CCGandSRLparse;
import edu.uw.easysrl.syntax.parser.SRLParser.JointSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.PipelineSRLParser;
import edu.uw.easysrl.syntax.parser.SRLParser.SemanticParser;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

public class WebDemo extends AbstractHandler {

	private final ParsePrinter printer = ParsePrinter.HTML_PRINTER;
	private final SRLParser parser;

	private WebDemo() throws IOException {

		parser = makeParser();
	}

	@Override
	public void handle(final String target, final Request baseRequest, final HttpServletRequest request,
			final HttpServletResponse response) throws IOException, ServletException {
		final String sentence = baseRequest.getParameter("sentence");
		response.setContentType("text/html; charset=utf-8");
		response.setStatus(HttpServletResponse.SC_OK);
		doParse(sentence, response.getWriter());
		baseRequest.setHandled(true);
	}

	private SRLParser makeParser() throws IOException {
		final int nbest = 10;
		final String folder = Util.getHomeFolder() + "/Downloads/lstm_models/model_questions";
		final String pipelineFolder = folder + "/pipeline";
		final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));
		final PipelineSRLParser pipeline = new PipelineSRLParser(EasySRL.makeParser(pipelineFolder, 0.0001,
				ParsingAlgorithm.ASTAR, 200000, false, Optional.empty(), nbest, 100), Util.deserialize(new File(
				pipelineFolder, "labelClassifier")), posTagger);

		final SRLParser jointAstar = new SemanticParser(
				new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(folder, 0.005, ParsingAlgorithm.ASTAR,
						20000, true, Optional.empty(), nbest, 100), posTagger), pipeline),
						CompositeLexicon.makeDefault(new File(folder, "lexicon")));

		return jointAstar;
	}

	public static void main(final String[] args) throws Exception {
		final Server server = new Server(Integer.valueOf(args[0]));
		server.setHandler(new WebDemo());
		server.start();
		server.join();
	}

	// @formatter:off

	private void doParse(String sentence, final PrintWriter response) {

		if (sentence == null) {
			sentence = "";
		}
		response.println("<html><head><title>EasySRL Parser Demo</title></head><script src=\"https://ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js\"></script>\n"
				+ "<!-- Latest compiled and minified CSS -->\n"
				+ "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css\" integrity=\"sha512-dTfge/zgoMYpP7QbHy4gWMEGsbsdZeCXz7irItjcC3sPUFtf0kuFbDz/ixG7ArTxmDjLXDmezHubeNikyKGVyQ==\" crossorigin=\"anonymous\">\n"
				+ "\n"
				+ "<!-- Optional theme -->\n"
				+ "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap-theme.min.css\" integrity=\"sha384-aUGj/X2zp5rLCbBxumKTCw2Z50WgIr1vs/PFN4praOTvYXWlVyh2UtNUU0KAUhAX\" crossorigin=\"anonymous\">\n"
				+ "\n"
				+ "<!-- Latest compiled and minified JavaScript -->\n"
				+ "<script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js\" integrity=\"sha512-K1qjQ+NcF2TYO/eI3M6v8EiNYZfA95pQumfvcVrTHtwQVDG+aHRqLi/ETn2uB+1JqwYqVG3LIvdm9lj6imS/pQ==\" crossorigin=\"anonymous\"></script>"
				+ "<style> hr{display:block; height: 1px; border:0; border-top: 1px solid #000000; margin: 1em 50; padding: 0; } </style>"
				+ "<body style=\"padding:20\">");
		response.println("<h1><font face=\"arial\">EasySRL Parser Demo</font></h1>");
		response.println("      <div><a href=https://github.com/mikelewis0/EasySRL>Download here!</a></div>      \n"
				+ "        <br><form action=\"\" method=\"get\">\n"
				+ "      <input type=\"text\"  size=\"40\" name=\"sentence\" value=\"" + sentence + "\"> \n"
				+ "      <input type=\"submit\" value=\"Parse!\">" + "    </form>");

		// Very lazy tokenizer...
		sentence = sentence.replaceAll("\\.", " .");
		sentence = sentence.replaceAll("\\,", " ,");
		sentence = sentence.replaceAll("\\?", " ?");
		sentence = sentence.replaceAll("\\:", " :");
		sentence = sentence.replaceAll("' ", " ' ");
		sentence = sentence.replaceAll("'s ", " 's ");
		sentence = sentence.replaceAll("n't ", " n't ");
		sentence = sentence.replaceAll("!", " !");
		sentence = sentence.replaceAll("  +", " ");
		sentence = sentence.trim();

		if (!sentence.isEmpty()) {
			System.out.println(sentence);

			final List<InputWord> words = InputWord.listOf(sentence.split(" "));
			if (words.size() > parser.getMaxSentenceLength()) {
				response.println("<br>Max sentence length: " + parser.getMaxSentenceLength());
				return;
			}
			final List<CCGandSRLparse> parses = parser.parseTokens(words);

			response.println(printer.printJointParses(parses, 0));
		}

	}
}
