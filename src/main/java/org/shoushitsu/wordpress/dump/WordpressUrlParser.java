package org.shoushitsu.wordpress.dump;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordpressUrlParser {

	public static SiteAndSlug parsePostUrl(String url) {
		Matcher matcher = POST_URL_PATTERN.matcher(url);
		if (!matcher.matches()) {
			return null;
		}
		return new SiteAndSlug(matcher.group(POST_URL_SITE_GROUP), matcher.group(POST_URL_SLUG_GROUP));
	}

	private static final Pattern POST_URL_PATTERN = Pattern.compile("(?:http://)?([-\\w]+?.wordpress.com)/\\d{4}/\\d{2}/\\d{2}/(.+)/?");

	private static final int POST_URL_SITE_GROUP = 1;

	private static final int POST_URL_SLUG_GROUP = 2;

	public static final class SiteAndSlug {

		public final String site;

		public final String slug;

		public SiteAndSlug(String site, String slug) {
			this.site = site;
			this.slug = slug;
		}

		@Override
		public String toString() {
			return String.format("{site='%s', slug='%s'}", site, slug);
		}

	}

}
