package edu.uw.easysrl.dependencies;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SRLFrame implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public final static SRLLabel NONE = SRLLabel.make("NONE", false);
	public final static SRLLabel UNLABELLED_ARGUMENT = SRLLabel.make(
			"UNLABELLED", false);

	public final static SRLLabel ARG0 = SRLLabel.make("ARG0", true);
	public final static SRLLabel ARG1 = SRLLabel.make("ARG1", true);
	public final static SRLLabel ARG2 = SRLLabel.make("ARG2", true);
	public final static SRLLabel ARG3 = SRLLabel.make("ARG3", true);
	public final static SRLLabel ARG4 = SRLLabel.make("ARG4", true);
	public final static SRLLabel ARG5 = SRLLabel.make("ARG5", true);
	public final static SRLLabel ARGA = SRLLabel.make("ARGA", true);

	public final static SRLLabel DIR = SRLLabel.make("DIR", false);
	public final static SRLLabel LOC = SRLLabel.make("LOC", false);
	public final static SRLLabel MNR = SRLLabel.make("MNR", false);
	public final static SRLLabel TMP = SRLLabel.make("TMP", false);
	public final static SRLLabel EXT = SRLLabel.make("EXT", false);
	public final static SRLLabel REC = SRLLabel.make("REC", false);
	public final static SRLLabel PRD = SRLLabel.make("PRD", false);
	public final static SRLLabel PNC = SRLLabel.make("PNC", false);
	public final static SRLLabel CAU = SRLLabel.make("CAU", false);
	public final static SRLLabel DIS = SRLLabel.make("DIS", false);
	public final static SRLLabel ADV = SRLLabel.make("ADV", false);
	public final static SRLLabel MOD = SRLLabel.make("MOD", false);
	public final static SRLLabel NEG = SRLLabel.make("NEG", false);

	public static class SRLLabel implements Serializable {

		// Not using an enum because hash codes don't persist on serialization.

		private static final long serialVersionUID = 1L;
		private final String name;
		private final boolean isCoreArgument;
		private final static Map<String, SRLLabel> cache = Collections
				.synchronizedMap(new HashMap<String, SRLLabel>());
		private final int id;
		private final static AtomicInteger numRoles = new AtomicInteger();

		private SRLLabel(final String name, final boolean isCoreArgument) {
			super();
			this.name = name;
			this.isCoreArgument = isCoreArgument;
			this.id = numRoles.getAndIncrement();
		}

		public boolean isCoreArgument() {
			return isCoreArgument;
		}

		public static int numberOfLabels() {
			return numRoles.get();
		}

		/**
		 * A unique numeric identifier. NOT guaranteed to be stable across JVMs.
		 */
		public int getID() {
			return id;
		}

		private Object readResolve() {
			return make(this.name, this.isCoreArgument);
		}

		public static SRLLabel fromString(String label) {
			if (label.startsWith("ARGM")) {
				final int i = label.indexOf("-");
				label = label.substring(i + 1);
				return make(label, false);
			} else {
				final int i = label.indexOf("-");
				if (i > -1) {
					label = label.substring(0, i);
				}
				return make(label, true);
			}

		}

		static SRLLabel make(final String name, final boolean isCoreArgument) {
			SRLLabel result = cache.get(name);
			if (result == null) {
				result = new SRLLabel(name, isCoreArgument);
				cache.put(name, result);
			}

			return result;
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public boolean equals(final Object other) {
			return this == other;
		}

		private static Collection<SRLLabel> getAllLabels() {
			return cache.values();
		}

		@Override
		public String toString() {
			return name;
		}

	}

	public static Collection<SRLLabel> getAllSrlLabels() {
		return SRLLabel.getAllLabels();
	}

}
