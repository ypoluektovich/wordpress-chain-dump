package org.shoushitsu.wordpress.dump;

import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class MakeEpub {

	private static final Logger log = LoggerFactory.getLogger("wp-chain-epub");

	public static void main(String[] args) throws IOException {
		Book book = new Book();

		addBookInfo(book);

		addChapters(book);

		log.info("Writing book file");
		try (BufferedOutputStream bookOut = new BufferedOutputStream(Files.newOutputStream(Paths.get(args[0])))) {
			new EpubWriter().write(book, bookOut);
		}
	}

	private static void addBookInfo(Book book) throws IOException {
		log.info("Loading book info...");
		JSONObject bookInfo = new JSONObject(new String(
				Files.readAllBytes(Paths.get(PostChainDumper.BOOK_INFO_FILENAME)),
				StandardCharsets.UTF_8
		));
		book.getMetadata().addTitle(bookInfo.getString(PostChainDumper.PROP_TITLE));
		JSONArray authors = bookInfo.getJSONArray(PostChainDumper.PROP_AUTHOR);
		for (int i = 0; i < authors.length(); ++i) {
			book.getMetadata().addAuthor(new Author(authors.getString(i)));
		}
	}

	private static void addChapters(Book book) throws IOException {
		for (int index = 0; ; ++index) {
			Path chapterInfoPath = Paths.get(PostChainDumper.getChapterInfoFileName(index));
			if (Files.notExists(chapterInfoPath)) {
				break;
			}

			log.info("Adding chapter #{}", index);

			Properties chapterInfo = new Properties();
			try (BufferedReader chapterInfoReader = Files.newBufferedReader(chapterInfoPath, StandardCharsets.UTF_8)) {
				chapterInfo.load(chapterInfoReader);
			}
			String chapterContentFileName = PostChainDumper.getChapterContentFileName(index);
			BufferedReader contentReader = Files.newBufferedReader(Paths.get(chapterContentFileName), StandardCharsets.UTF_8);
			book.addSection(chapterInfo.getProperty(PostChainDumper.PROP_TITLE), new Resource(contentReader, chapterContentFileName));
		}
	}

}
