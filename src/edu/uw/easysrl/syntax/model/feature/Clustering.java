package edu.uw.easysrl.syntax.model.feature;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class Clustering implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = 2354324383814232945L;
	private final Map<String, Integer> clusterIdentifier;
	private final String name;

	public Clustering(final File file, final boolean idIsFirst) {
		clusterIdentifier = loadClusters(file, idIsFirst);
		name = file.getName();
	}

	private static Map<String, Integer> loadClusters(final File file, final boolean idIsFirst) {
		final Map<String, Integer> clusterIdentifier = new HashMap<>();

		final int id = idIsFirst ? 0 : 1;
		final int word = idIsFirst ? 1 : 0;

		try {
			Files.lines(file.toPath()).map(s -> s.split("\t"))
					.forEach(s -> clusterIdentifier.put(s[word], Integer.valueOf(s[id])));
		} catch (final IOException e) {
			throw new RuntimeException("Clustering not found: " + file);
		}

		return clusterIdentifier;
	}

	public Integer getCluster(final String word) {
		final Integer result = clusterIdentifier.get(word);

		if (result != null) {
			return result;
		} else if (word.endsWith(".")) {
			return getCluster(word.substring(0, word.length() - 1));

		} else {
			return -1;
		}
	}

	public String getName() {
		return name;
	}
}