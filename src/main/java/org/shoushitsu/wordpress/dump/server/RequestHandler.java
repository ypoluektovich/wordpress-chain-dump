package org.shoushitsu.wordpress.dump.server;

import org.simpleframework.http.Path;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

class RequestHandler implements Container {

	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	void awaitShutdown() throws InterruptedException {
		shutdownLatch.await();
	}

	@Override
	public void handle(Request req, Response resp) {
		Path path = req.getAddress().getPath();
		log.info("Request: " + path);
		try {
			switch (path.getPath()) {
				case "/stop":
					log.info("Server stop command received");
					resp.setCode(200);
					shutdownLatch.countDown();
					break;
				default:
					log.warn("Unsupported request: " + path);
					break;
			}
		} finally {
			try {
				resp.close();
			} catch (IOException e) {
				log.error("Error while closing a response", e);
			}
		}
	}

}
