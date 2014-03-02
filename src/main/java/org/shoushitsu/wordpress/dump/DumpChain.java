package org.shoushitsu.wordpress.dump;

import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubWriter;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

public class DumpChain {

	private static final Logger log = LoggerFactory.getLogger("wp-chain-loader");

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("Arguments: <first URL in chain> <target EPUB file>");
			return;
		}
		Book book = new Book();
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			PostChainDumper.dump(client, args[0], new MyCallback(book));
		}
		log.info("Writing book file");
		try (BufferedOutputStream bookOut = new BufferedOutputStream(Files.newOutputStream(Paths.get(args[1])))) {
			new EpubWriter().write(book, bookOut);
		}

	}

	private static class MyCallback implements PostChainDumperCallback {

		private final Book book;

		private String chapterTitle;

		private final StringBuilder chapter = new StringBuilder();

		public MyCallback(Book book) {
			this.book = book;
		}

		private void writeln(String line) {
			chapter.append(line);
			chapter.append('\n');
		}

		@Override
		public Logger getLogger() {
			return log;
		}

		@Override
		public boolean impossible() {
			return false;
		}

		@Override
		public boolean badUrl(int index, String url) {
			return false;
		}

		@Override
		public void startChapter(int index) {
			writeln("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
			writeln("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">");
			writeln("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
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
		public void bookTitle(String title) {
			book.getMetadata().addTitle(title);
		}

		@Override
		public void chapterTitle(String title) {
			writeln("<head>");
			writeln("<title>" + title + "</title>");
			writeln("</head>");
			writeln("<body>");
			writeln("<h1>" + title + "</h1>");
			chapterTitle = title;
		}

		@Override
		public void author(String author) {
			book.getMetadata().addAuthor(new Author(author));
		}

		@Override
		public void chapterLine(String line) {
			writeln(line);
		}

		@Override
		public boolean endChapter(int index) {
			writeln("</body>");
			writeln("</html>");

			String contentFileName = getChapterContentFileName(index);
			try {
				log.debug("Writing cleaned content into {}", contentFileName);
				Files.write(Paths.get(contentFileName), Collections.singleton(chapter.toString()), StandardCharsets.UTF_8);
			} catch (IOException e) {
				log.error("Error while saving cleaned chapter to disk", e);
			}
			try {
				book.addSection(chapterTitle, new Resource(new StringReader(chapter.toString()), contentFileName));
			} catch (IOException e) {
				log.error("Impossible error while adding chapter {} to the book", e);
				return impossible();
			}
			chapter.setLength(0);
			return true;
		}
	}

	public static String getChapterContentFileName(int index) {
		return String.format("%06d.html", index);
	}

}
