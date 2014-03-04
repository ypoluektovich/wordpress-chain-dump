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

class RequestHandler implements Container {

	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

	private static final JSONObject OK = new JSONObject(Collections.singletonMap("status", "ok"));
	private static final JSONObject WHAT = new JSONObject(Collections.singletonMap("status", "what"));

	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

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
			default:
				log.info("Unsupported request");
				return WHAT;
		}
	}

}
