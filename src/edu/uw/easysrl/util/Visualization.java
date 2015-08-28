package edu.uw.easysrl.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.corpora.ParallelCorpusReader.Sentence;
import edu.uw.easysrl.main.ParsePrinter;

public class Visualization {

	public static void main(String[] args) throws IOException {
		Iterator<Sentence> sentences = ParallelCorpusReader.READER.readCorpus(false);
		String lastFile = null;
	     BufferedWriter out = null;
	     File folder = new File(Util.getHomeFolder(), "ccg_html");
	     folder.mkdir();

	     int id = 0;
		while (sentences.hasNext()) {
			Sentence sentence = sentences.next();
			String file = sentence.getCCGBankDependencyParse().getFile();
			if (!file.equals(lastFile)) {
				id = 1;
			}
			File outputFile = new File(folder, file + "." + id + ".html");
			System.out.println(outputFile.getAbsolutePath());
			out = new BufferedWriter(new FileWriter(outputFile));
			id++;

			out.write(ParsePrinter.HTML_PRINTER.print(Arrays.asList(sentence.getCcgbankParse()), 1));
			out.close();
			lastFile = file;

		}
	}

}
