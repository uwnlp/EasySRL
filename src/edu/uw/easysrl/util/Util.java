package edu.uw.easysrl.util;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

public class Util {
	public static int indexOfAny(final String haystack, final String needles) {
		for (int i = 0; i < haystack.length(); i++) {
			for (int j = 0; j < needles.length(); j++) {
				if (haystack.charAt(i) == needles.charAt(j)) {
					return i;
				}
			}
		}

		return -1;
	}

	public static File getFile(final String path) {
		return new File(path.replace("~", System.getProperty("user.home")));
	}

	public static File getHomeFolder() {
		return new File(System.getProperty("user.home"));
	}

	public static void serialize(final Object object, final File file) {

		try {
			final FileOutputStream fileOut = new FileOutputStream(file);
			final ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(object);
			out.close();
			fileOut.close();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

	}

	private final static DecimalFormat twoDP = new DecimalFormat("#.00");;
	@SuppressWarnings("unused")
	private final static DecimalFormat exponentialFormat = new DecimalFormat("0.#E0");

	public static String twoDP(final double number) {
		return twoDP.format(number);
	}

	@SuppressWarnings("unchecked")
	public static <O> O deserialize(final File file) {
		O o = null;
		try {
			final FileInputStream fileIn = new FileInputStream(file);
			final ObjectInputStream in = new ObjectInputStream(fileIn);
			o = (O) in.readObject();
			in.close();
			fileIn.close();
			return o;
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public static Iterable<String> readFile(final File filePath) throws java.io.IOException {
		return new Iterable<String>() {

			@Override
			public Iterator<String> iterator() {

				try {
					return readFileLineByLine(filePath);
				} catch (final IOException e) {
					throw new RuntimeException(e);
				}
			}
		};

	}

	public static String executeCommand(final String command) throws IOException {
		final Process process = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", command });

		final BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));

		final StringBuilder output = new StringBuilder();
		String line;
		while ((line = input.readLine()) != null) {
			output.append(line);
			output.append("\n");
		}
		input.close();

		return output.toString();

	}

	public static <T extends Comparable<T>> int compareSortedArrays(final T[] t1, final T[] t2) {
		if (t1.length != t2.length) {
			return t1.length - t2.length;
		}

		for (int i = 0; i < t1.length; i++) {
			final int result = t1[i].compareTo(t2[i]);
			if (result != 0) {
				return result;
			}
		}

		return 0;
	}

	public static void writeStringToFile(final String text, final File filePath) throws java.io.IOException {
		final BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
		out.write(text);
		out.close();
	}

	public static Iterator<String> readFileLineByLine(final File filePath) throws java.io.IOException {
		return new Iterator<String>() {

			// Open the file that is the first
			// command line parameter
			InputStream fstream = new FileInputStream(filePath);
			{
				if (filePath.getName().endsWith(".gz")) {
					// Automatically unzip zipped files.
					fstream = new GZIPInputStream(fstream);
				}
			}

			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String next = br.readLine();

			@Override
			public boolean hasNext() {

				final boolean result = (next != null);
				if (!result) {
					try {
						br.close();
					} catch (final IOException e) {
						throw new RuntimeException(e);
					}
				}

				return result;
			}

			@Override
			public String next() {
				final String result = next;
				try {
					next = br.readLine();
				} catch (final IOException e) {
					throw new RuntimeException(e);
				}
				return result;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	public static String dropBrackets(final String cat) {
		if (cat.startsWith("(") && cat.endsWith(")") && findClosingBracket(cat) == cat.length() - 1) {
			return cat.substring(1, cat.length() - 1);
		} else {
			return cat;
		}
	}

	public static int findClosingBracket(final String source) {
		return findClosingBracket(source, 0);
	}

	public static int findClosingBracket(final String source, final int startIndex) {
		int openBrackets = 0;
		for (int i = startIndex; i < source.length(); i++) {
			if (source.charAt(i) == '(') {
				openBrackets++;
			} else if (source.charAt(i) == ')') {
				openBrackets--;
			}

			if (openBrackets == 0) {
				return i;
			}
		}

		throw new Error("Mismatched brackets in string: " + source);
	}

	/**
	 * Finds the first index of a needle character in the haystack, that is not nested in brackets.
	 */
	public static int findNonNestedChar(final String haystack, final String needles) {
		int openBrackets = 0;

		for (int i = 0; i < haystack.length(); i++) {
			if (haystack.charAt(i) == '(' || haystack.charAt(i) == '[' || haystack.charAt(i) == '{') {
				openBrackets++;
			} else if (haystack.charAt(i) == ')' || haystack.charAt(i) == ']' || haystack.charAt(i) == '}') {
				openBrackets--;
			} else if (openBrackets == 0) {
				for (int j = 0; j < needles.length(); j++) {
					if (haystack.charAt(i) == needles.charAt(j)) {
						return i;
					}
				}
			}
		}

		return -1;
	}

	public static List<File> findAllFiles(final File folder, final String regex) {
		return Util.findAllFiles(folder, new FilenameFilter() {
			@Override
			public boolean accept(final File dir, final String name) {
				return name.matches(regex);
			}
		});
	}

	private static List<File> findAllFiles(final File folder, final FilenameFilter filter) {
		final List<File> result = new ArrayList<>();

		findAllFiles(folder, filter, result);
		return result;
	}

	private static void findAllFiles(final File folder, final FilenameFilter filter, final List<File> result) {
		final File[] files = folder.listFiles();
		if (files != null) {
			for (final File file : files) {
				if (file.isDirectory()) {
					findAllFiles(file, filter, result);
				} else if (filter.accept(file, file.getName())) {
					result.add(file.getAbsoluteFile());
				}
			}
		}
	}

	public static class LruCache<A> extends LinkedHashMap<A, A> {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final int maxEntries;

		public LruCache(final int maxEntries) {
			super(maxEntries + 1, 1.0f, true);
			this.maxEntries = maxEntries;
		}

		/**
		 * Returns <tt>true</tt> if this <code>LruCache</code> has more entries than the maximum specified when it was
		 * created.
		 *
		 * <p>
		 * This method <em>does not</em> modify the underlying <code>Map</code>; it relies on the implementation of
		 * <code>LinkedHashMap</code> to do that, but that behavior is documented in the JavaDoc for
		 * <code>LinkedHashMap</code>.
		 * </p>
		 *
		 * @param eldest
		 *            the <code>Entry</code> in question; this implementation doesn't care what it is, since the
		 *            implementation is only dependent on the size of the cache
		 * @return <tt>true</tt> if the oldest
		 * @see java.util.LinkedHashMap#removeEldestEntry(Map.Entry)
		 */
		@Override
		protected boolean removeEldestEntry(final Map.Entry<A, A> eldest) {
			return super.size() > maxEntries;
		}

		public A getCached(final A key) {
			A result = get(key);
			if (result == null) {
				result = key;
				put(result, result);
			}
			return result;
		}
	}

	public static double[] subtract(final double[] vector1, final double[] vector2) {
		assert (vector1.length == vector2.length);
		final double[] result = new double[vector1.length];
		for (int i = 0; i < vector1.length; i++) {
			result[i] = vector1[i] - vector2[i];
		}
		return result;
	}

	public static void add(final double[] vector1, final double[] vector2) {
		assert (vector1.length == vector2.length);
		for (int i = 0; i < vector1.length; i++) {
			vector1[i] = vector1[i] + vector2[i];
		}
	}

	public static class Logger implements Serializable {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		private final File file;
		private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");

		public Logger(final File file) {
			this.file = file;
		}

		public void log(final String message) {
			try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file, true)))) {

				final String toWrite = format.format(Calendar.getInstance().getTime()) + "\t" + message;
				System.err.println(toWrite);
				out.println(toWrite);

			} catch (final IOException e) {
				System.err.println("ERROR WRITING TO LOG FILE: " + file.getAbsolutePath());
			}
		}
	}

	public static <R, C, V> void add(final Table<R, C, Multiset<V>> table, final R row, final C column, final V value) {
		Multiset<V> multiset = table.get(row, column);
		if (multiset == null) {
			multiset = HashMultiset.create();
			table.put(row, column, multiset);
		}
		multiset.add(value);
	}

	public static <K, V> void add(final Map<K, Multiset<V>> map, final K key, final V value) {
		Multiset<V> multiset = map.get(key);
		if (multiset == null) {
			multiset = HashMultiset.create();
			map.put(key, multiset);
		}
		multiset.add(value);
	}

	public static boolean isCapitalized(final String word) {
		final char c = word.charAt(0);
		return 'A' <= c && c <= 'Z';
	}

	public static class Scored<T> implements Comparable<Scored<T>> {
		private final T object;
		private final double score;

		@Override
		public int compareTo(final Scored<T> o) {
			return Doubles.compare(o.score, score);
		}

		public Scored(final T object, final double score) {
			super();
			this.object = object;
			this.score = score;
		}

		public T getObject() {
			return object;
		}

		public double getScore() {
			return score;
		}

	}

	public static void runJobsInParallel(final Collection<Runnable> tasks, final int numThreads) {
		final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		try {
			final List<Future<?>> futures = new ArrayList<>(tasks.size());
			for (final Runnable task : tasks) {
				futures.add(executor.submit(task));

			}

			for (final Future<?> future : futures) {
				future.get();
			}
			executor.shutdown();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public static void runInBackground(final Runnable task) {
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.submit(task);

	}

	public static String percentage(final int count, final int size) {
		return Util.twoDP((100.0 * count / size)) + "%";
	}

	public static <T> void print(final Multiset<T> multiset, final int number) {
		int i = 0;
		for (final T type : Multisets.copyHighestCountFirst(multiset).elementSet()) {
			System.out.println(type + ": " + multiset.count(type));
			i++;
			if (i == number) {
				break;
			}
		}
		System.out.println();
	}

	public static void debugHook() {
	}

	public static Properties loadProperties(final File file) {
		try {
			final Properties defaultProps = new Properties();
			final FileInputStream in = new FileInputStream(file);
			defaultProps.load(in);
			in.close();
			return defaultProps;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

	}

	public static String maybeBracket(final String input, final boolean bracket) {
		return bracket ? "(" + input + ")" : input;
	}

	// zip(Stream.of("a", "b", "c"), Stream.of("1", "2", "3"), (x,y) -> x + y)
	// returns
	// Stream.of("a1", "b2", "c3")
	public static <A, B, C> Stream<C> zip(Stream<? extends A> a, Stream<? extends B> b,
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
