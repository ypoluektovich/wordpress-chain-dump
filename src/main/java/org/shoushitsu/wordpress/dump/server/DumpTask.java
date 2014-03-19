package org.shoushitsu.wordpress.dump.server;

import nl.siegmann.epublib.domain.Book;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.shoushitsu.wordpress.dump.EpubDumperCallback;
import org.shoushitsu.wordpress.dump.PostChainDumper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

class DumpTask implements Runnable {

	static interface Callback {

		void failed(DumpTask task);

		boolean done(DumpTask task, Book book, Logger log);

	}

	private static final AtomicLong INDEX_SOURCE = new AtomicLong();

	private static final int PENDING = -1;
	private static final int READY = -2;
	private static final int FAILED = -3;

	private static final String STATUS = "status";
	private static final JSONObject JSON_PENDING = new JSONObject(Collections.singletonMap(STATUS, "pending"));
	private static final JSONObject JSON_READY = new JSONObject(Collections.singletonMap(STATUS, "ready"));
	private static final JSONObject JSON_FAILED = new JSONObject(Collections.singletonMap(STATUS, "failed"));

	private final String url;

	private final Callback callback;

    private String bookTitle;

	private volatile int progress = PENDING;

	DumpTask(String url, Callback callback) {
		this.url = url;
		this.callback = callback;
	}

	String getUrl() {
		return url;
	}

    String getBookTitle() {
        return bookTitle;
    }

    @Override
	public void run() {
		Logger log = LoggerFactory.getLogger(DumpTask.class.getName() + '.' + INDEX_SOURCE.incrementAndGet());
		log.info("Starting processing with URL {}", url);

		progress = 0;
		EpubDumperCallback dumperCallback = new MyEpubDumperCallback(log);
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			PostChainDumper.dump(client, url, dumperCallback);
		} catch (IOException e) {
			log.error("Error while closing HTTP client");
		} catch (InterruptedException e) {
			log.warn(e.getMessage());
			progress = FAILED;
		}
		if (progress == FAILED) {
			return;
		}

		log.info("Reporting success");
		boolean saved = callback.done(this, dumperCallback.getBook(), log);

		if (saved) {
			log.info("Done!");
			progress = READY;
		} else {
			log.warn("Failed to save the book");
			progress = FAILED;
		}
	}

	JSONObject getStatusAsJson() {
		int progress = this.progress;
		switch (progress) {
			case PENDING:
				return JSON_PENDING;
			case READY:
				return JSON_READY;
			case FAILED:
				return JSON_FAILED;
			default:
				JSONObject json = new JSONObject();
				json.put(STATUS, "working");
				json.put("progress", progress);
				return json;
		}
	}

	private class MyEpubDumperCallback extends EpubDumperCallback {
		MyEpubDumperCallback(Logger log) {
			super(log);
		}

		@Override
		public void fetchException(IOException e) {
			DumpTask.this.progress = FAILED;
			callback.failed(DumpTask.this);
		}

		@Override
		public void saveUnparsedPost(int index, byte[] post) {
			// do nothing
		}

        @Override
        protected void onBookTitle(String title) {
            DumpTask.this.bookTitle = title;
        }

        @Override
		protected void onEndChapter(int index) {
			DumpTask.this.progress = index;
		}

		@Override
		public boolean impossible() {
			DumpTask.this.progress = FAILED;
			callback.failed(DumpTask.this);
			return false;
		}

		@Override
		public boolean badUrl(int index, String url) {
			DumpTask.this.progress = FAILED;
			callback.failed(DumpTask.this);
			return false;
		}

	}
}
