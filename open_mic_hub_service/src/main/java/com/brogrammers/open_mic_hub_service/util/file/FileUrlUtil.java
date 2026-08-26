package com.brogrammers.open_mic_hub_service.util.file;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class FileUrlUtil {

    private static String staticBaseUrl;

    @Value("${server.base-url}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        staticBaseUrl = baseUrl;
    }

    public static URI getFileUri(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }

        // Normalize and encode the path
        String cleanPath = relativePath.replace("\\", "/").replaceFirst("^/+", "");
        String encodedPath = URLEncoder.encode(cleanPath, StandardCharsets.UTF_8)
                .replace("%2F", "/");

        String urlPath = staticBaseUrl + "media/" + encodedPath;
        log.debug("Generated URL: {}", urlPath);
        return URI.create(urlPath);
    }
}
