package org.shoushitsu.wordpress.dump.server;

import org.simpleframework.http.Path;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

class RequestHandler implements Container {

	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	void awaitShutdown() throws InterruptedException {
		shutdownLatch.await();
	}

	@Override
	public void handle(Request req, Response resp) {
		Path path = req.getAddress().getPath();

		System.err.println("Request: " + path);

		try {
			switch (path.getPath()) {
				case "/stop":
					resp.setCode(200);
					shutdownLatch.countDown();
					break;
				default:
					break;
			}
		} finally {
			try {
				resp.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

}
