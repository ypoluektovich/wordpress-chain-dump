package org.shoushitsu.wordpress.dump;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostChainDumper {

	private static final int INDEX_PLACEHOLDER = 0;

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

	public static void dump(CloseableHttpClient client, String firstUrl, PostChainDumperCallback callback) throws IOException {
		PostChainDumper dumper = new PostChainDumper(client, callback);
		dumper.enqueue(firstUrl);
		dumper.run();
	}


	private final CloseableHttpClient client;

	private final PostChainDumperCallback callback;

	private final Logger log;

	private final BlockingQueue<String> urlQueue = new LinkedBlockingQueue<>();

	private final ConcurrentMap<String, Integer> indexByUrl = new ConcurrentHashMap<>();

	private final AtomicInteger indexSource = new AtomicInteger();

	private volatile boolean savedBookInfo;

	private final Set<String> knownAuthors = new HashSet<>();

	private PostChainDumper(CloseableHttpClient client, PostChainDumperCallback callback) {
		this.client = client;
		this.callback = callback;
		this.log = callback.getLogger();
	}

	private void enqueue(String url) {
		if (indexByUrl.putIfAbsent(url, INDEX_PLACEHOLDER) == null) {
			log.trace("Enqueueing url: {}", url);
			indexByUrl.put(url, indexSource.incrementAndGet());
			urlQueue.offer(url);
		}
	}

	private void run() throws IOException {
		String url;
		while ((url = urlQueue.poll()) != null) {
			processNode(url, indexByUrl.get(url));
		}
	}

	private boolean processNode(String url, int index) {
		log.info("Processing url with index {}: {}", index, url);
		callback.startChapter(index);

		WordpressUrlParser.SiteAndSlug siteAndSlug = WordpressUrlParser.parsePostUrl(url);
		if (siteAndSlug == null) {
			log.error("Can't parse URL: {}", url);
			return callback.badUrl(index, url);
		}
		log.debug("Parsed url as: {}", siteAndSlug);
		long startTime = System.currentTimeMillis();
		byte[] content;
		IOException fetchException = null;
		for (int attempt = 1; ; ++attempt) {
			try {
				content = fetchContent(siteAndSlug, url);
				break;
			} catch (PostNotFoundException e) {
				callback.fetchException(e);
				return false;
			} catch (IOException e) {
				if (fetchException == null) {
					fetchException = e;
				} else {
					fetchException.addSuppressed(e);
				}
			}
			if (attempt == 3) {
				log.info("Couldn't fetch in {} attempts, aborting", attempt);
				callback.fetchException(fetchException);
				return false;
			} else {
				log.info("Attempt #{} at fetching content failed, will wait {} seconds before trying again...", attempt);
				try {
					Thread.sleep(attempt * 1000);
				} catch (InterruptedException e) {
					log.info("Wait was interrupted!", e);
					return false;
				}
			}
		}
		callback.saveUnparsedPost(index, content);
		cleanUpContent(content);

		log.info("Processed chapter #{} in {} ms total", index, System.currentTimeMillis() - startTime);
		callback.endChapter(index);
		return true;
	}

	private byte[] fetchContent(WordpressUrlParser.SiteAndSlug siteAndSlug, String url) throws IOException {
		log.debug("Fetching content");
		long startTime = System.currentTimeMillis();
		byte[] content;
		while (true) {
			HttpGet request = new HttpGet(WordpressApiUrlBuilder.getPostBySiteAndSlug(siteAndSlug.site, siteAndSlug.slug));
			if (!savedBookInfo) {
				try {
					request.setURI(new URIBuilder(request.getURI()).addParameter("meta", "site").build());
				} catch (URISyntaxException e) {
					logImpossibleException(e);
					savedBookInfo = true;
				}
			}
			int statusCode;
			try (CloseableHttpResponse response = client.execute(request)) {
				statusCode = response.getStatusLine().getStatusCode();
				if (statusCode == 200) {
					long contentLength = response.getEntity().getContentLength();
					ByteArrayOutputStream baos = new ByteArrayOutputStream(contentLength < 0 ? (1 << 15) : (int) contentLength);
					response.getEntity().writeTo(baos);
					content = baos.toByteArray();
					break;
				}
			}
			log.debug("Status code {} is not 200! Attempting to follow redirect...", statusCode);
			if (statusCode == 404) {
				try (CloseableHttpResponse head = client.execute(
						RequestBuilder.head()
								.setUri(url)
								.setConfig(
										RequestConfig.copy(RequestConfig.DEFAULT)
												.setRedirectsEnabled(false)
												.build()
								)
								.build()
				)) {
					statusCode = head.getStatusLine().getStatusCode();
					log.debug("HEAD returned code: {}", statusCode);
					if (statusCode == 301) {
						url = head.getFirstHeader("Location").getValue();
						siteAndSlug = WordpressUrlParser.parsePostUrl(url);
						continue;
					}
				}
			}
			throw new PostNotFoundException(url);
		}
		log.info("Fetched {} bytes in {} ms", content.length, System.currentTimeMillis() - startTime);
		return content;
	}

	private static class PostNotFoundException extends IOException {
		PostNotFoundException(String url) {
			super("couldn't find a post at location " + url);
		}
	}

	private void cleanUpContent(byte[] content) {
		JSONObject json = new JSONObject(new String(content, StandardCharsets.UTF_8));
		if (!savedBookInfo) {
			processBookInfo(json);
		}
		processChapterInfo(json);
		processChapterContent(json);
	}

	private void processBookInfo(JSONObject json) {
		String title = json.getJSONObject("meta").getJSONObject("data").getJSONObject("site").getString("name");
		log.info("Book title: {}", title);
		callback.bookTitle(title);
		savedBookInfo = true;
	}

	private void processChapterInfo(JSONObject json) {
		String title = json.getString("title");
		log.info("Chapter title: {}", title);
		callback.chapterTitle(title);

		String author = json.getJSONObject("author").getString("nice_name");
		if (!knownAuthors.contains(author)) {
			knownAuthors.add(author);
			callback.author(author);
		}
	}

	private void processChapterContent(JSONObject json) {
		for (String line : json.getString("content").split("\n")) {
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
				callback.chapterLine(line);
			}
		}
	}

	private void logImpossibleException(Exception e) {
		log.error("Impossible!", e);
		callback.impossible();
	}

}
