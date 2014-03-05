package org.shoushitsu.wordpress.dump.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

abstract class TaskCleanupQueue<I> implements Runnable {

	private final class Entry implements Comparable<Entry> {

		private final I item;

		private final long timestamp;

		Entry(I item, long timestamp) {
			this.item = item;
			this.timestamp = timestamp;
		}

		@Override
		public int compareTo(Entry other) {
			return Long.compare(this.timestamp, other.timestamp);
		}

	}

	private static final AtomicLong INDEX_SOURCE = new AtomicLong();

	protected final Logger log = LoggerFactory.getLogger(TaskCleanupQueue.class.getName() + '.' + INDEX_SOURCE.incrementAndGet());

	private final NavigableSet<Entry> queue = new ConcurrentSkipListSet<>();

	void enqueue(I item, long delay) {
		log.info("Enqueueing item {} to be purged in {} ms", item, delay);
		queue.add(new Entry(item, System.currentTimeMillis() + delay));
	}

	@Override
	public void run() {
		Entry entry;
		while ((entry = queue.pollFirst()) != null && entry.timestamp < System.currentTimeMillis()) {
			log.info("Purging item {}", entry.item);
			purge(entry.item);
		}
	}

	protected abstract void purge(I item);

}
