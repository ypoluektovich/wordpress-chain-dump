package org.shoushitsu.wordpress.dump;

import java.io.IOException;

public class PostNotFoundException extends IOException {

	PostNotFoundException(String url) {
		super(url);
	}

	public String getUrl() {
		return super.getMessage();
	}

	@Override
	public String getMessage() {
		return "Couldn't find a post at location " + getUrl();
	}

}
