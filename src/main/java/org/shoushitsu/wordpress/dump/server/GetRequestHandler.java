package org.shoushitsu.wordpress.dump.server;

import io.otonashi.cache.ContentSource;
import io.otonashi.cache.ContentStorage;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

class GetRequestHandler implements Container {

    private final Logger log = LoggerFactory.getLogger(GetRequestHandler.class);

    private final DumpRequestHandler dumpHandler;

    private final ContentStorage bookCache;

    GetRequestHandler(DumpRequestHandler dumpHandler, ContentStorage bookCache) {
        this.dumpHandler = dumpHandler;
        this.bookCache = bookCache;
    }

    @Override
    public void handle(Request req, Response resp) {
        try {
            String url = req.getParameter("url");
            if (url == null) {
                resp.setCode(400);
                resp.close();
                return;
            }
            try (ContentSource source = bookCache.getSource(url)) {
                String bookTitle = dumpHandler.getBookTitle(url);
                if (source == null || bookTitle == null) {
                    resp.setCode(404);
                    resp.close();
                    return;
                }
                resp.setCode(200);
                resp.setContentType("application/epub+zip");
                resp.setValue("Content-Disposition", "attachment; filename*=UTF-8''" + encode(bookTitle) + ".epub");
                try (OutputStream out = resp.getOutputStream()) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = source.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error while handling a get request", e);
        }
    }

    private static String encode(String bookTitle) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = bookTitle.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            sb.append('%').append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
