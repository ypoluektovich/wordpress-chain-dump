package org.shoushitsu.wordpress.dump.server;

import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {

	private static final Logger log = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Arguments: <port to listen on>");
			return;
		}

		RequestHandler handler = new RequestHandler();

		Connection connection;
		try {
			ContainerServer server = new ContainerServer(handler);
			connection = new SocketConnection(server);
			connection.connect(new InetSocketAddress(Integer.parseInt(args[0])));
		} catch (IOException e) {
			log.error("Failed to start the server", e);
			System.exit(1);
			return;
		}

		log.info("Started the server");

		try {
			handler.awaitShutdown();
		} catch (InterruptedException e) {
			log.error("Interrupted while waiting for shutdown", e);
			return;
		}

		log.warn("Stopping the server");
		try {
			connection.close();
		} catch (IOException e) {
			log.error("Error while shutting the server down", e);
		}
		log.info("Sayonara");
	}

}
