package org.shoushitsu.wordpress.dump;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostChainDumper {

	private static final int INDEX_PLACEHOLDER = -1;

	private static final Pattern NEWLINE_PATTERN = Pattern.compile("\n");

	private static final int BEFORE_ARTICLE = 0;
	private static final int INSIDE_ARTICLE = 1;
	private static final int AFTER_ARTICLE = 2;

	private static final String BOOK_TITLE_PREFIX = "<meta property=\"og:site_name\"";
	private static final String TITLE_PREFIX = "<meta property=\"og:title\"";

	public static final String BOOK_INFO_FILENAME = "book.info";
	public static final String PROP_URL = "url";
	public static final String PROP_TITLE = "title";

	private static final Pattern CONTENT_START_PATTERN = Pattern.compile("class=\"(?:entry-)?content\"");

	private static final String NAV_LINK_SUBSTRING = "<a";
	private static final Set<String> NAV_LINK_NEXT_MARKERS = new HashSet<>();
	private static final Pattern NAV_LINK_PATTERN;
	private static final int NAV_LINK_URL_GROUP;
	private static final int NAV_LINK_MARKER_GROUP;

	static {
		NAV_LINK_NEXT_MARKERS.add("Next");
		StringBuilder sb = new StringBuilder();
		for (String marker : NAV_LINK_NEXT_MARKERS) {
			sb.append(marker).append('|');
		}
		sb.append("Last|Previous");
		NAV_LINK_PATTERN = Pattern.compile("<a(?:\\s++title=\"[^\"]++\")?\\s++href=\"([^\"]++)\"\\s*+>\\s*+(" + sb.toString() + ")\\s++Chapter\\s*+</a>");
		NAV_LINK_URL_GROUP = 1;
		NAV_LINK_MARKER_GROUP = 2;
	}


	private final Logger log;

	private final CloseableHttpClient client;

	private final BlockingQueue<String> urlQueue = new LinkedBlockingQueue<>();

	private final ConcurrentMap<String, Integer> indexByUrl = new ConcurrentHashMap<>();

	private final AtomicInteger indexSource = new AtomicInteger();

	private final Properties bookInfo = new Properties();

	private volatile boolean savedBookInfo;

	private final Properties chapterInfo = new Properties();

	private final List<String> chapterLines = new ArrayList<>();

	public PostChainDumper(CloseableHttpClient client, String loggerName) {
		this.log = LoggerFactory.getLogger(loggerName);
		this.client = client;
	}


	public void enqueue(String url) {
		if (indexByUrl.putIfAbsent(url, INDEX_PLACEHOLDER) == null) {
			log.trace("Enqueueing url: {}", url);
			indexByUrl.put(url, indexSource.getAndIncrement());
			urlQueue.offer(url);
		}
	}


	public void run() throws IOException {
		String url;
		while ((url = urlQueue.poll()) != null) {
			processNode(url, indexByUrl.get(url));
		}
	}

	private void processNode(String thisUrl, int index) throws IOException {
		log.info("Processing url with index {}: {}", index, thisUrl);
		long startTime = System.currentTimeMillis();
		byte[] content = fetchContent(thisUrl);
		saveOriginalContent(index, content);
		cleanUpContent(thisUrl, content);
		saveCleanedContent(index);
		log.info("Processed chapter #{} in {} ms total", index, System.currentTimeMillis() - startTime);
	}

	private byte[] fetchContent(String url) throws IOException {
		log.debug("Fetching content");
		long startTime = System.currentTimeMillis();
		byte[] content;
		try (CloseableHttpResponse response = client.execute(new HttpGet(url))) {
			long contentLength = response.getEntity().getContentLength();
			ByteArrayOutputStream baos = new ByteArrayOutputStream(contentLength < 0 ? 8192 : (int) contentLength);
			response.getEntity().writeTo(baos);
			content = baos.toByteArray();
		}
		log.info("Fetched {} bytes in {} ms", content.length, System.currentTimeMillis() - startTime);
		return content;
	}

	private void saveOriginalContent(int index, byte[] content) throws IOException {
		String pathString = String.format("%06d.orig.html", index);
		log.debug("Saving uncleaned content to {}", pathString);
		Files.write(Paths.get(pathString), content);
	}

	private void cleanUpContent(String thisUrl, byte[] content) {
		chapterInfo.clear();
		chapterInfo.setProperty(PROP_URL, thisUrl);

		chapterLines.clear();

		try (Scanner scanner = new Scanner(new ByteArrayInputStream(content), StandardCharsets.UTF_8.name())) {
			scanner.useDelimiter(NEWLINE_PATTERN);
			int state = BEFORE_ARTICLE;

			while (scanner.hasNext()) {
				String line = scanner.next();
				switch (state) {
					case BEFORE_ARTICLE:
						if (!savedBookInfo && line.startsWith(BOOK_TITLE_PREFIX)) {
							processBookTitle(line);
						} else if (line.startsWith(TITLE_PREFIX)) {
							processChapterTitle(line);
						} else if (CONTENT_START_PATTERN.matcher(line).find()) {
							state = INSIDE_ARTICLE;
						}
						break;
					case INSIDE_ARTICLE:
						if (line.contains("id=\"jp-post-flair\"")) {
							state = AFTER_ARTICLE;
						} else {
							processLineInArticle(line);
						}
						break;
					default:
						throw new IllegalStateException(Integer.toString(state));
				}
				if (state == AFTER_ARTICLE) {
					break;
				}
			}
		}
	}

	private void processBookTitle(String line) {
		String title = parseMetaPropertyValue(line, BOOK_TITLE_PREFIX.length());
		log.info("Book title: {}", title);
		bookInfo.setProperty(PROP_TITLE, title);
	}

	private void processChapterTitle(String line) {
		String title = parseMetaPropertyValue(line, TITLE_PREFIX.length());
		log.info("Chapter title: {}", title);
		chapterInfo.setProperty(PROP_TITLE, title);
	}

	private static String parseMetaPropertyValue(String line, int offset) {
		int titleStart = line.indexOf('"', offset) + 1;
		int titleEnd = line.lastIndexOf('"');
		return line.substring(titleStart, titleEnd);
	}

	private void processLineInArticle(String line) {
		boolean navigationLine = false;
		if (line.contains(NAV_LINK_SUBSTRING)) {
			Matcher matcher = NAV_LINK_PATTERN.matcher(line);
			while (matcher.find()) {
				navigationLine = true;
				String linkMarker = matcher.group(NAV_LINK_MARKER_GROUP);
				log.trace("Found navigation link with marker {}", linkMarker);
				if (NAV_LINK_NEXT_MARKERS.contains(linkMarker)) {
					String url = matcher.group(NAV_LINK_URL_GROUP);
					log.debug("Link to next chapter: {}", url);
					enqueue(url);
				}
			}
		}
		if (!navigationLine) {
			chapterLines.add(line);
		}
	}

	private void saveCleanedContent(int index) throws IOException {
		String fileName = BOOK_INFO_FILENAME;
		if (!savedBookInfo && !bookInfo.isEmpty()) {
			log.debug("Writing book info to {}", fileName);
			try (BufferedWriter out = Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8)) {
				bookInfo.store(out, null);
			}
			savedBookInfo = true;
		}

		fileName = getChapterInfoFileName(index);
		log.debug("Writing chapter info to {}", fileName);
		try (BufferedWriter out = Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8)) {
			chapterInfo.store(out, null);
		}

		fileName = getChapterContentFileName(index);
		log.debug("Writing cleaned content into {}", fileName);
		try (BufferedWriter out = Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8)) {
			writeln(out, "<?xml version=\"1.0\" encoding=\"utf-8\"?>");
			writeln(out, "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">");
			writeln(out, "<html xmlns=\"http://www.w3.org/1999/xhtml\">");

			writeln(out, "<head>");
			writeln(out, "<title>" + chapterInfo.getProperty(PROP_TITLE) + "</title>");
			writeln(out, "</head>");

			writeln(out, "<body>");
			writeln(out, "<h1>" + chapterInfo.getProperty(PROP_TITLE) + "</h1>");
			for (String line : chapterLines) {
				writeln(out, line);
			}
			writeln(out, "</body>");

			writeln(out, "</html>");
		}
	}

	public static String getChapterInfoFileName(int index) {
		return String.format("%06d.info", index);
	}

	public static String getChapterContentFileName(int index) {
		return String.format("%06d.html", index);
	}

	private static void writeln(BufferedWriter out, String line) throws IOException {
		out.write(line);
		out.newLine();
	}

}
