/*
 * Copyright (C) 2012 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uw.easysrl.lemmatizer;

/* author: Michael Schmitz <schmmd@cs.washington.edu>
 *
 * This is a light wrapper for the JFLEX-generated code from the original lex
 * morpha stemmer.  It provides a nicer interface and handles exceptions.
 */

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class MorphaStemmer {
	private static final Pattern whitespace = Pattern.compile("\\s+");

	private final static Map<String, String> cache = new HashMap<>();

	/***
	 * Stem the supplied token.
	 *
	 * @throws IllegalArgumentException
	 *             token contains whitespace
	 **/
	public static String stemToken(final String token) {
		String result = cache.get(token);

		if (result == null) {
			if (whitespace.matcher(token).find()) {
				throw new IllegalArgumentException(
						"Token may not contain a space: " + token);
			}
			result = morpha(cleanText(token), false);
			synchronized (cache) {
				cache.put(token, result);
			}
		}

		return result;
	}

	/***
	 * Stem the supplied token using supplemental postag information.
	 *
	 * @throws IllegalArgumentException
	 *             token contains whitespace
	 **/
	public static String stemToken(final String token, final String postag) {
		if (whitespace.matcher(token).find()) {
			throw new IllegalArgumentException(
					"Token may not contain a space: " + token);
		}
		return morpha(cleanText(token) + "_" + postag, true);
	}

	private static String cleanText(final String text) {
		return text.replaceAll("_", "-");
	}

	/***
	 * Run the morpha algorithm on the specified string.
	 **/
	public static String morpha(final String text, final boolean tags) {
		if (text.isEmpty()) {
			return "";
		}

		final String[] textParts = whitespace.split(text);

		final StringBuilder result = new StringBuilder();
		try {
			for (int i = 0; i < textParts.length; i++) {
				final Morpha morpha = new Morpha(
						new StringReader(textParts[i]), tags);

				if (result.length() != 0) {
					result.append(" ");
				}

				result.append(morpha.next());
			}
		}
		// yes, Morpha is cool enough to throw Errors
		// usually when the text contains underscores
		catch (final Error e) {
			return text;
		} catch (final java.io.IOException e) {
			return text;
		}

		return result.toString();
	}
}
