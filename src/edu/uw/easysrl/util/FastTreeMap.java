package edu.uw.easysrl.util;

import java.util.Iterator;

/**
 * Tree map designed for use in chart cells.
 *
 * Compared to the JDK TreeMap, it has: a) Faster putIfAbsent method, by doing it in one search (not a lookup, then an
 * insert). b) Faster iterator, by maintaining a linked list through the elements (not faffing around with a successor
 * algorithm). c) Doesn't support removal of elements (which I don't need, and would break implementation of b.)
 *
 * Turns out that the simple binary search tree is faster than red-black tree maps for this application...
 */
public class FastTreeMap<Key, Value> {

	/** NULL when the tree is empty */
	private Node<Key, Value> root;

	/**
	 * Dummy first element for linked list.
	 */
	private final Node<Key, Value> first;

	// Last element added to the map.
	private Node<Key, Value> last;

	private int size;
	/** All leaf nodes are black empty nodes that share this one instance. */
	private final Node<Key, Value> EMPTY = new Node<Key, Value>() {

		@Override
		public Node<Key, Value> putIfAbsent(final Key k, final Value v, final Node<Key, Value> last,
				final Node<Key, Value> empty) {
			last.nextNode = new Node<>(k, v, empty);
			return last.nextNode;
		}

		@Override
		public Node<Key, Value> getNode(final Key key) {
			return null;
		}
	};

	public FastTreeMap() {
		root = EMPTY;
		first = new Node<>();
		last = first;
	}

	/**
	 * Adds an element to the map if it is not already present.
	 *
	 * Returns true if the element was added, false otherwise.
	 */
	public boolean putIfAbsent(final Key key, final Value value) {
		root = root.putIfAbsent(key, value, last, EMPTY);

		if (last.nextNode != null) {
			// If we inserted a new node, update the linked list through elements, and return true.
			last = last.nextNode;
			size++;
			return true;
		} else {
			// Already had an entry with this key.
			return false;
		}
	}

	public int size() {
		return size;
	}

	/** Returns null if not found. */
	public Value get(final Key key) {
		final Node<Key, Value> n = root.getNode(key);
		if (n != null) {
			return n.value;
		} else {
			return null;
		}
	}

	public boolean contains(final Key key) {
		return root.getNode(key) != null;
	}

	private static class Node<Key, Value> {
		private final Key key;
		private final Value value;

		private Node<Key, Value> left;
		private Node<Key, Value> right;
		private Node<Key, Value> nextNode;

		/** Used by Empty */
		protected Node() {
			key = null;
			value = null;
		}

		/** Nodes always begin red */
		public Node(final Key k, final Value v, final Node<Key, Value> empty) {
			key = k;
			value = v;
			left = empty;
			right = empty;
		}

		Node<Key, Value> putIfAbsent(final Key k, final Value v, final Node<Key, Value> last,
				final Node<Key, Value> empty) {
			@SuppressWarnings("unchecked")
			final int comparison = ((Comparable<? super Key>) k).compareTo(key);
			if (comparison > 0) {
				left = left.putIfAbsent(k, v, last, empty);
			} else if (comparison < 0) {
				right = right.putIfAbsent(k, v, last, empty);
			} else {
				// Already have entry with this key.
				return this;
			}

			return this;
		}

		/** Returns the node for this key, or null. */
		public Node<Key, Value> getNode(final Key k) {
			@SuppressWarnings("unchecked")
			final int comparison = ((Comparable<? super Key>) k).compareTo(key);
			if (comparison > 0) {
				return left.getNode(k);
			} else if (comparison < 0) {
				return right.getNode(k);
			} else {
				return this;
			}
		}
	}

	/**
	 * Iterates over values in insertion order.
	 */
	public Iterable<Value> values() {

		return new Iterable<Value>() {
			@Override
			public Iterator<Value> iterator() {
				return new Iterator<Value>() {

					private Node<Key, Value> current = first;

					@Override
					public boolean hasNext() {
						return current.nextNode != null;
					}

					@Override
					public Value next() {
						current = current.nextNode;
						return current.value;
					}

				};

			}
		};
	}
}