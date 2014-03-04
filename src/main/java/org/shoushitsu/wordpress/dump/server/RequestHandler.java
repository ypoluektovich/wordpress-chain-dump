package org.shoushitsu.wordpress.dump.server;

import org.json.JSONObject;
import org.simpleframework.http.Path;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

class RequestHandler implements Container {

	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

	private static final JSONObject OK = makeOkResponse();

	private static JSONObject makeOkResponse() {
		return new JSONObject(Collections.singletonMap("status", "ok"));
	}

	private static final JSONObject WHAT = new JSONObject(Collections.singletonMap("status", "what"));

	private static final String P_URL = "url";
	private static final JSONObject MISSING_URL;
	static {
		MISSING_URL = new JSONObject();
		MISSING_URL.put("status", "missing_param");
		MISSING_URL.put("params", Arrays.asList(P_URL));
	}

	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	private final ConcurrentMap<String, DumpTask> taskByUrl = new ConcurrentHashMap<>();

	private final ExecutorService executor;

	RequestHandler(ExecutorService executor) {
		this.executor = executor;
	}

	void awaitShutdown() throws InterruptedException {
		shutdownLatch.await();
	}

	@Override
	public void handle(Request req, Response resp) {
		Path path = req.getAddress().getPath();
		log.info("Request: " + path);

		JSONObject responseJson = handle(req);

		resp.setCode(200);
		resp.setContentType("application/json; charset=utf-8");
		try (OutputStreamWriter out = new OutputStreamWriter(resp.getOutputStream(), StandardCharsets.UTF_8)) {
			responseJson.write(out);
		} catch (IOException e) {
			log.error("Error while writing the response", e);
		}
	}

	private JSONObject handle(Request req) {
		switch (req.getAddress().getPath().getPath()) {
			case "/stop":
				log.info("Server stop command received");
				shutdownLatch.countDown();
				return OK;
			case "/dump":
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
			default:
				log.info("Unsupported request");
				return WHAT;
		}
	}

	private class MyDumpTaskCallback implements DumpTask.Callback {
		@Override
		public void failed(DumpTask task) {
			taskByUrl.remove(task.getUrl(), task);
		}
	}

}
