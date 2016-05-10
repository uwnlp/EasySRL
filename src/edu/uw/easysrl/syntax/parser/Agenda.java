package edu.uw.easysrl.syntax.parser;

import java.util.Comparator;

import edu.uw.easysrl.syntax.model.AgendaItem;

public interface Agenda extends Iterable<AgendaItem> {
	AgendaItem peek();

	AgendaItem poll();

	boolean add(AgendaItem item);

	default boolean isEmpty() {
		return size() == 0;
	}

	int size();

	Comparator<AgendaItem> comparator();
}