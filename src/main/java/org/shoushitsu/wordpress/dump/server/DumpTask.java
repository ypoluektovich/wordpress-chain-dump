package org.shoushitsu.wordpress.dump.server;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

class DumpTask implements Runnable {

	static interface Callback {
		void failed(DumpTask task);
	}

	private static final AtomicLong INDEX_SOURCE = new AtomicLong();

	private static final int PENDING = -1;
	private static final int READY = -2;
	private static final int FAILED = -3;

	private static final String STATUS = "status";
	private static final JSONObject JSON_PENDING = new JSONObject(Collections.singletonMap(STATUS, "pending"));
	private static final JSONObject JSON_READY = new JSONObject(Collections.singletonMap(STATUS, "ready"));

	private final String url;

	private final Callback callback;

	private volatile int progress = PENDING;

	DumpTask(String url, Callback callback) {
		this.url = url;
		this.callback = callback;
	}

	String getUrl() {
		return url;
	}

	@Override
	public void run() {
		Logger log = LoggerFactory.getLogger(DumpTask.class.getName() + '.' + INDEX_SOURCE.incrementAndGet());
		log.info("Starting processing with URL {}", url);
		for (progress = 0; progress < 5; ++progress) {
			log.info("Progress is {}, simulating work...", progress);
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				log.warn("Interrupted");
				progress = FAILED;
				callback.failed(this);
				return;
			}
		}
		log.info("Done!");
		progress = READY;
	}

	JSONObject getStatusAsJson() {
		int progress = this.progress;
		switch (progress) {
			case PENDING:
				return JSON_PENDING;
			case READY:
				return JSON_READY;
			default:
				JSONObject json = new JSONObject();
				json.put(STATUS, "working");
				json.put("progress", progress);
				return json;
		}
	}

}
