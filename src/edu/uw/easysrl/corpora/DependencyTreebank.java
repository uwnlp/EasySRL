package edu.uw.easysrl.corpora;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import edu.uw.easysrl.util.Util;

class DependencyTreebank implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Table<String, Integer, SyntacticDependencyParse> readCorpus(File folder)
			throws IOException {
		Table<String, Integer, SyntacticDependencyParse> result = HashBasedTable
				.create();
		for (File file : Util.findAllFiles(folder, ".*.deps")) {
			String name = file.getName().substring(0,
					file.getName().indexOf("."));
			// System.out.println(name);
			Iterator<String> lines = Util.readFileLineByLine(file);
			int sentenceNumber = 0;
			while (lines.hasNext()) {
				result.put(name, sentenceNumber, readParse(lines));
				sentenceNumber++;
			}
		}

		return result;
	}

	private SyntacticDependencyParse readParse(Iterator<String> lines) {
		Table<Integer, Integer, String> headToModifierToLabel = HashBasedTable
				.create();
		List<String> words = new ArrayList<>();
		// 3 and _ CC _ _ 2 COORD _ _

		while (lines.hasNext()) {
			String line = lines.next();
			if (line.isEmpty()) {
				break;
			}
			String[] fields = line.split("\t");

			int index = Integer.parseInt(fields[0]) - 1;
			String word = fields[1];
			int head = Integer.parseInt(fields[6]) - 1;
			String label = fields[7];

			if (head >= 0) {
				headToModifierToLabel.put(head, index, label);
			}

			words.add(word);

		}
		return new SyntacticDependencyParse(headToModifierToLabel);

	}

	static class SyntacticDependencyParse implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		private SyntacticDependencyParse(
				Table<Integer, Integer, String> headToModifierToLabel) {
			this.headToModifierToLabel = headToModifierToLabel;
		}

		private final Table<Integer, Integer, String> headToModifierToLabel;

		public Collection<Cell<Integer, Integer, String>> getDependencies() {
			return headToModifierToLabel.cellSet();
		}

		private Integer pathLength(int from, int to, Set<Integer> visited) {
			if (from == to)
				return 0;
			visited.add(from);
			Integer up = pathLength(to, visited, headToModifierToLabel
					.row(from).keySet());
			if (up != null)
				return up;
			Integer down = pathLength(to, visited, headToModifierToLabel
					.column(from).keySet());
			return down;
		}

		private Integer pathLength(int to, Set<Integer> visited,
				Collection<Integer> reachableNodes) {
			for (int reachable : reachableNodes) {
				if (!visited.contains(reachable)) {
					Integer distance = pathLength(reachable, to, visited);
					if (distance != null)
						return 1 + distance;
				}
			}

			return null;
		}
	}
}
