package com.brogrammers.open_mic_hub_service.config.file;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the root directory used for uploaded media.
 *
 * <p>Previously this switched on {@code os.name} between three hard-coded absolute paths, which
 * meant uploads failed on any machine whose user was not {@code ubuntu}. It is now a single
 * configurable property ({@code app.filepath}) that defaults to {@code ./uploads}.
 */
@Configuration
@Slf4j
public class FileConfig {

    @Value("${app.filepath:./uploads}")
    private String filePath;

    @PostConstruct
    void ensureDirectoryExists() {
        try {
            Files.createDirectories(getRoot());
            log.info("[FileConfig] Upload root ready at {}", getRoot());
        } catch (IOException e) {
            log.error("[FileConfig] Could not create upload root {}: {}", filePath, e.getMessage());
        }
    }

    /** Absolute, normalized upload root. Used to confine writes and reject traversal. */
    public Path getRoot() {
        return Paths.get(filePath).toAbsolutePath().normalize();
    }

    public String getFilePath() {
        return getRoot().toString();
    }
}
