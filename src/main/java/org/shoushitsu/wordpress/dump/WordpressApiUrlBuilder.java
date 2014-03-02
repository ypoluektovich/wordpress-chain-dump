package org.shoushitsu.wordpress.dump;

public class WordpressApiUrlBuilder {

	private static final String API_URL_PREFIX = "https://public-api.wordpress.com/rest/v1";

	public static String getPostBySiteAndSlug(String site, String slug) {
		return String.format(API_URL_PREFIX + "/sites/%s/posts/slug:%s", site, slug);
	}

}
