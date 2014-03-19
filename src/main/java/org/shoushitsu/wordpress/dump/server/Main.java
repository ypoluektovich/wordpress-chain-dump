package org.shoushitsu.wordpress.dump.server;

import io.otonashi.cache.StorageCallback;
import io.otonashi.cache.file.BadStorageRootException;
import io.otonashi.cache.file.FileStorage;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main {

	private static final Logger log = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		byte exitCode = main0(args);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	private static byte main0(String[] args) {
		if (args.length != 2) {
			System.err.println("Arguments: <port to listen on> <file cache dir>");
			return 0;
		}
		int port;
		try {
			port = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			System.err.println("Invalid port number: " + args[0]);
			return 1;
		}
		Path cacheRoot;
		try {
			cacheRoot = Paths.get(args[1]);
		} catch (InvalidPathException e) {
			System.err.println("Invalid cache dir: " + args[1]);
			return 1;
		}

        FileStorage bookCache;
        try {
            bookCache = new FileStorage(
                    cacheRoot,
                    new StorageCallback() {
                        @Override
                        public void releaseFailed(Throwable throwable) {
                            log.error("Failed to release cache entry, resource leak possible!", throwable);
                        }
                    }
            );
        } catch (BadStorageRootException e) {
            log.error("Failed to initialize book storage", e);
            return 1;
        }

		ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

		RequestDispatchingHandler dispatchingHandler = new RequestDispatchingHandler();
		StopRequestHandler stopHandler = new StopRequestHandler();
        DumpRequestHandler dumpHandler = new DumpRequestHandler(bookCache, executor);
        dispatchingHandler.setHandler("/stop", stopHandler);
        dispatchingHandler.setHandler("/dump", dumpHandler);
        dispatchingHandler.setHandler("/get", new GetRequestHandler(dumpHandler, bookCache));

        Connection connection;
		try {
			ContainerServer server = new ContainerServer(dispatchingHandler);
			connection = new SocketConnection(server);
			connection.connect(new InetSocketAddress(port));
		} catch (IOException e) {
			log.error("Failed to start the server", e);
			return 1;
		}

		log.info("Started the server on port {}", port);

		try {
			stopHandler.awaitShutdown();
		} catch (InterruptedException e) {
			log.error("Interrupted while waiting for shutdown", e);
			return 0;
		}

		log.warn("Stopping the HTTP server");
		try {
			connection.close();
		} catch (IOException e) {
			log.error("Error while shutting the server down", e);
		}

		log.warn("Stopping the executor service");
		executor.shutdownNow();
		return 0;
	}

}
