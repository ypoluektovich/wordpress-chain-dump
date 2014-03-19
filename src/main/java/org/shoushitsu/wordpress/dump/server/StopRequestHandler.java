package org.shoushitsu.wordpress.dump.server;

import org.json.JSONObject;
import org.simpleframework.http.Request;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

class StopRequestHandler extends AJsonHandler {

	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	StopRequestHandler() {
		super(LoggerFactory.getLogger(StopRequestHandler.class));
	}

	void awaitShutdown() throws InterruptedException {
		shutdownLatch.await();
	}

	@Override
	protected JSONObject handle(Request req) {
		log.info("Server stop command received");
		shutdownLatch.countDown();
		return OK;
	}

}
