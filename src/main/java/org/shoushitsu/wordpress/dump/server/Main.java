package org.shoushitsu.wordpress.dump.server;

import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {

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
			System.err.println("Failed to start server");
			e.printStackTrace();
			System.exit(1);
			return;
		}

		System.err.println("Started server");

		try {
			handler.awaitShutdown();
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting for shutdown");
			e.printStackTrace();
			return;
		}

		try {
			connection.close();
		} catch (IOException e) {
			System.err.println("Error while closing connection");
			e.printStackTrace();
		}
		System.err.println("Exiting");
	}

}
