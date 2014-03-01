package org.shoushitsu.wordpress.dump;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class DumpChain {

	public static void main(String[] args) throws Exception {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			PostChainDumper dumper = new PostChainDumper(client, "wp-chain-loader");
			dumper.enqueue(args[0]);
			dumper.run();
		}
	}

}
