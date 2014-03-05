package org.shoushitsu.wordpress.dump.server;

import org.json.JSONObject;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

abstract class AJsonHandler implements Container {

	protected static final JSONObject OK = makeOkResponse();

	protected static JSONObject makeOkResponse() {
		return new JSONObject(Collections.singletonMap("status", "ok"));
	}


	protected final Logger log;

	protected AJsonHandler(Logger log) {
		this.log = log;
	}

	@Override
	public final void handle(Request req, Response resp) {
		resp.setCode(200);
		resp.setContentType("application/json; charset=utf-8");
		try (OutputStreamWriter out = new OutputStreamWriter(resp.getOutputStream(), StandardCharsets.UTF_8)) {
			handle(req).write(out);
		} catch (IOException e) {
			log.error("Error while writing the response", e);
		}
	}

	protected abstract JSONObject handle(Request req);

}
