package org.shoushitsu.wordpress.dump.server;

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class RequestDispatchingHandler implements Container {

	private static final Logger log = LoggerFactory.getLogger(RequestDispatchingHandler.class);

	private final ConcurrentMap<String, Container> handlerByRequestPath = new ConcurrentHashMap<>();

	void setHandler(String path, Container handler) {
		handlerByRequestPath.put(path, handler);
	}

	@Override
	public void handle(Request req, Response resp) {
		String path = req.getAddress().getPath().getPath();
		log.info("Request: " + path);

		Container handler = handlerByRequestPath.get(path);
		if (handler == null) {
			resp.setCode(404);
			try {
				resp.close();
			} catch (IOException e) {
				log.error("Error while closing the 404 response");
			}
		} else {
			handler.handle(req, resp);
		}
	}

}
