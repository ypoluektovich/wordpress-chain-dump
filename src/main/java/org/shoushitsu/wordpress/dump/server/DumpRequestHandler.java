package org.shoushitsu.wordpress.dump.server;

import io.otonashi.cache.ContentSink;
import io.otonashi.cache.ContentStorage;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.epub.EpubWriter;
import org.json.JSONObject;
import org.simpleframework.http.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class DumpRequestHandler extends AJsonHandler {

	private static final String P_URL = "url";
	private static final JSONObject MISSING_URL;
	static {
		MISSING_URL = new JSONObject();
		MISSING_URL.put("status", "missing_param");
		MISSING_URL.put("params", Arrays.asList(P_URL));
	}

	private final ConcurrentMap<String, DumpTask> taskByUrl = new ConcurrentHashMap<>();

	private final ContentStorage bookCache;

	private final TaskCleanupQueue<String> taskCleanupQueue = new TaskCleanupQueue<String>() {
		@Override
		protected void purge(String item) {
			taskByUrl.remove(item);
            bookCache.expire(item);
		}
	};

	private final ScheduledExecutorService executor;

	DumpRequestHandler(ContentStorage bookCache, ScheduledExecutorService executor) {
		super(LoggerFactory.getLogger(DumpRequestHandler.class));
		this.bookCache = bookCache;
		this.executor = executor;

		executor.scheduleWithFixedDelay(taskCleanupQueue, 0, 1, TimeUnit.SECONDS);
	}

    String getBookTitle(String firstChapterUrl) {
        DumpTask dumpTask = taskByUrl.get(firstChapterUrl);
        return dumpTask == null ? null : dumpTask.getBookTitle();
    }

	@Override
	protected JSONObject handle(Request req) {
		String url = req.getParameter(P_URL);
		if (url == null) {
			log.info("Bad request: missing parameter: url");
			return MISSING_URL;
		}
		url = url.trim();
		log.info("Asked to dump URL: {}", url);

		DumpTask newTask = new DumpTask(url, new MyDumpTaskCallback());
		DumpTask registeredTask = taskByUrl.putIfAbsent(url, newTask);
		if (registeredTask == null) {
			log.info("Scheduling new dump task");
			registeredTask = newTask;
			executor.execute(registeredTask);
		}

		JSONObject statusAsJson = registeredTask.getStatusAsJson();
		log.info("Returning status: {}", statusAsJson);
		JSONObject resp = makeOkResponse();
		resp.put("task", statusAsJson);
		return resp;
	}

	private class MyDumpTaskCallback implements DumpTask.Callback {
		@Override
		public void failed(DumpTask task) {
			taskCleanupQueue.enqueue(task.getUrl(), 60 * 1000);
		}

		@Override
		public boolean done(DumpTask task, Book book, Logger log) {
            try (ContentSink sink = bookCache.getSink(task.getUrl())) {
                log.info("Writing book file");
                new EpubWriter().write(book, sink.getOutputStream());
                return true;
            } catch (IOException e) {
                log.error("Error while saving the book", e);
                return false;
            } finally {
                taskCleanupQueue.enqueue(task.getUrl(), 60 * 60 * 1000);
            }
		}
	}

}
