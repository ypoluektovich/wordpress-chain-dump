package org.shoushitsu.wordpress.dump;

import nl.siegmann.epublib.epub.EpubWriter;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DumpChain {

	private static final Logger log = LoggerFactory.getLogger("wp-chain-loader");

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("Arguments: <first URL in chain> <target EPUB file>");
			return;
		}
		EpubDumperCallback callback = new MyEpubDumperCallback();
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			PostChainDumper.dump(client, args[0], callback);
		}
		log.info("Writing book file");
		try (BufferedOutputStream bookOut = new BufferedOutputStream(Files.newOutputStream(Paths.get(args[1])))) {
			new EpubWriter().write(callback.getBook(), bookOut);
		}
	}

	private static class MyEpubDumperCallback extends EpubDumperCallback {
		MyEpubDumperCallback() {
			super(DumpChain.log);
		}

		@Override
		public void fetchException(IOException e) {
			throw new RuntimeException(e);
		}

		@Override
		public void saveUnparsedPost(int index, byte[] post) {
			String pathString = String.format("%06d.orig", index);
			log.debug("Saving unparsed post to {}", pathString);
			try {
				Files.write(Paths.get(pathString), post);
			} catch (IOException e) {
				log.error("Error while saving unparsed data", e);
			}
		}

		@Override
		public boolean impossible() {
			return false;
		}

		@Override
		public boolean badUrl(int index, String url) {
			return false;
		}

	}

}
