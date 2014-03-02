package org.shoushitsu.wordpress.dump;

import org.slf4j.Logger;

import java.io.IOException;

public interface PostChainDumperCallback {

	Logger getLogger();

	boolean impossible();

	boolean badUrl(int index, String url);

	void startChapter(int index);

	void fetchException(IOException e);

	void saveUnparsedPost(int index, byte[] post);

	void bookTitle(String title);

	void chapterTitle(String title);

	void author(String author);

	void chapterLine(String line);

	boolean endChapter(int index);

}
