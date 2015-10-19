package edu.uw.easysrl.main;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.uw.easysrl.main.EasySRL.ParsingAlgorithm;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.semantics.Lexicon;
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
		final String folder = Util.getHomeFolder() + "/Downloads/model";
		final String pipelineFolder = folder + "/pipeline";
		final POSTagger posTagger = POSTagger.getStanfordTagger(new File(pipelineFolder, "posTagger"));
		final PipelineSRLParser pipeline = new PipelineSRLParser(EasySRL.makeParser(pipelineFolder, 0.0001,
				ParsingAlgorithm.ASTAR, 200000, false), Util.deserialize(new File(pipelineFolder, "labelClassifier")),
				posTagger);

		final SRLParser jointAstar = new SemanticParser(new BackoffSRLParser(new JointSRLParser(EasySRL.makeParser(
				folder, 0.01, ParsingAlgorithm.ASTAR, 20000, true), posTagger), pipeline), new Lexicon(new File(folder,
						"lexicon")));

		return jointAstar;
	}

	public static void main(final String[] args) throws Exception {
		final Server server = new Server(8080);
		server.setHandler(new WebDemo());
		server.start();
		server.join();
	}

	// @formatter:off

	private void doParse(String sentence, final PrintWriter response) {
		if (sentence == null) {
			sentence = "";
		}
		response.println("<html><head><title>EasySRL Parser Demo</title></head>\n" + "<body>\n");
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
			final CCGandSRLparse parse = parser.parseTokens(InputWord.listOf(sentence.split(" ")));
			response.println(printer.print(parse.getCcgParse(), 0));
		}

	}
}
