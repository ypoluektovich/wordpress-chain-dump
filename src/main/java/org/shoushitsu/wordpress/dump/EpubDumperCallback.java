package org.shoushitsu.wordpress.dump;

import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.StringReader;

public abstract class EpubDumperCallback implements PostChainDumperCallback {

	protected final Logger log;

	private final Book book = new Book();

	private String chapterTitle;

	private final StringBuilder chapter = new StringBuilder();

	protected EpubDumperCallback(Logger log) {
		this.log = log;
	}

	final Book getBook() {
		return book;
	}

	private void writeln(String line) {
		chapter.append(line);
		chapter.append('\n');
	}

	@Override
	public final Logger getLogger() {
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
	public final void startChapter(int index) {
		writeln("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
		writeln("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">");
		writeln("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
	}

	@Override
	public final void bookTitle(String title) {
		book.getMetadata().addTitle(title);
	}

	@Override
	public final void chapterTitle(String title) {
		writeln("<head>");
		writeln("<title>" + title + "</title>");
		writeln("</head>");
		writeln("<body>");
		writeln("<h1>" + title + "</h1>");
		chapterTitle = title;
	}

	@Override
	public final void author(String author) {
		book.getMetadata().addAuthor(new Author(author));
	}

	@Override
	public final void chapterLine(String line) {
		writeln(line);
	}

	@Override
	public final boolean endChapter(int index) {
		writeln("</body>");
		writeln("</html>");

		try {
			book.addSection(
					chapterTitle,
					new Resource(
							new StringReader(chapter.toString()),
							String.format("%06d.html", index)
					)
			);
		} catch (IOException e) {
			log.error("Impossible error while adding chapter {} to the book", e);
			return impossible();
		} finally {
			chapter.setLength(0);
		}
		return true;
	}

}
