package org.shoushitsu.wordpress.dump.server;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.epub.EpubWriter;
import org.shoushitsu.wordpress.dump.WordpressUrlParser;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class FileCache {

	private final Path cacheRoot;

	FileCache(Path cacheRoot) {
		this.cacheRoot = cacheRoot;
	}

	boolean save(String url, Book book, Logger log) {
		WordpressUrlParser.SiteAndSlug siteAndSlug = WordpressUrlParser.parsePostUrl(url);
		try {
			Path siteDir = cacheRoot.resolve(siteAndSlug.site);
			log.debug("Ensuring existence of site dir");
			Files.createDirectories(siteDir);

			Path slugFile = siteDir.resolve(siteAndSlug.slug);
			log.info("Writing book file");
			try (BufferedOutputStream bookOut = new BufferedOutputStream(Files.newOutputStream(slugFile))) {
				new EpubWriter().write(book, bookOut);
			}
		} catch (IOException e) {
			log.error("Error while saving the book", e);
			return false;
		}
		return true;
	}

}
