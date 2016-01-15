package edu.uw.easysrl.syntax.evaluation;

import java.util.concurrent.atomic.AtomicInteger;

import edu.uw.easysrl.util.Util;

public class Results {

	private final AtomicInteger parseDependencies;
	private final AtomicInteger correctDependencies;
	private final AtomicInteger goldDependencies;

	public Results(final int parseDependencies, final int correctDependencies, final int goldDependencies) {
		this.parseDependencies = new AtomicInteger(parseDependencies);
		this.correctDependencies = new AtomicInteger(correctDependencies);
		this.goldDependencies = new AtomicInteger(goldDependencies);
	}

	public Results() {
		this(0, 0, 0);
	}

	public void add(final Results other) {
		parseDependencies.addAndGet(other.parseDependencies.get());
		correctDependencies.addAndGet(other.correctDependencies.get());
		goldDependencies.addAndGet(other.goldDependencies.get());
	}

	public double getRecall() {
		return (double) correctDependencies.get() / goldDependencies.get();
	}

	public double getPrecision() {
		return (double) correctDependencies.get() / parseDependencies.get();
	}

	public double getF1() {
		return 2 * (getPrecision() * getRecall()) / (getPrecision() + getRecall());
	}

	public boolean isEmpty() {
		return goldDependencies.get() == 0;
	}

	@Override
	public String toString() {
		final StringBuilder result = new StringBuilder();
		result.append("Precision = " + Util.twoDP(getPrecision() * 100));
		result.append('\n');
		result.append("Recall    = " + Util.twoDP(getRecall() * 100));
		result.append('\n');
		result.append("F1        = " + Util.twoDP(getF1() * 100));
		return result.toString();
	}

	public int getFrequency() {
		return goldDependencies.get();
	}
}