package edu.uw.easysrl.syntax.parser;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import edu.uw.easysrl.syntax.model.AgendaItem;

public class PriorityQueueAgenda implements Agenda {
	private final PriorityQueue<AgendaItem> queue;
	private final Comparator<AgendaItem> comparator;

	public PriorityQueueAgenda(final Comparator<AgendaItem> comparator) {
		this.comparator = comparator;
		this.queue = new PriorityQueue<>(1000, comparator);
	}

	@Override
	public Comparator<AgendaItem> comparator() {
		return comparator;
	}

	@Override
	public AgendaItem peek() {
		return queue.peek();
	}

	@Override
	public AgendaItem poll() {
		return queue.poll();
	}

	@Override
	public boolean add(AgendaItem item) {
		return queue.add(item);
	}

	@Override
	public int size() {
		return queue.size();
	}

	@Override
	public Iterator<AgendaItem> iterator() {
		return queue.iterator();
	}
}
