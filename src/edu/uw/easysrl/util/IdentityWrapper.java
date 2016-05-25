package edu.uw.easysrl.util;

import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/*
 * Uses provided equality and hashing functions to redefine the equivalence of an object with modifying it.
 */
public class IdentityWrapper<T> {
	private final T object;
	private int hash;
	private final BiPredicate<T, T> equality;
	private final ToIntFunction<T> hasher;

	public IdentityWrapper(final T object, final BiPredicate<T, T> equality, final ToIntFunction<T> hasher) {
		this.object = object;
		this.equality = equality;
		this.hasher = hasher;
		this.hash = 0;
	}

	public T getObject() {
		return object;
	}

	@Override
	public int hashCode() {
		if (hash == 0) {
			hash = hasher.applyAsInt(object);
		}
		return hash;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object other) {
		return equality.test(this.object, ((IdentityWrapper<T>) other).object);
	}
}
