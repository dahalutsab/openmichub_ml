package com.brogrammers.open_mic_hub_service.util.file;

import com.brogrammers.open_mic_hub_service.config.file.FileConfig;
import com.brogrammers.open_mic_hub_service.util.file.dto.FileSaveResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileHandlerUtil {

    private final FileConfig fileConfig;

    public FileSaveResponse saveFile(MultipartFile file, String additionalPath) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Invalid file");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        // Both halves of the name are sanitized. The extension used to be taken raw from the
        // client-supplied filename and concatenated into the path, so a crafted name could write
        // outside the upload root - and anything landing there is served from /media/**.
        String baseName = cleanFileName(getBaseName(originalFilename));
        String extension = cleanExtension(getFileExtension(originalFilename));
        if (baseName.isBlank()) {
            baseName = "file";
        }
        String uniqueFileName = baseName + "-" + System.currentTimeMillis()
                + (extension.isEmpty() ? "" : "." + extension);

        String relativePath = (additionalPath != null && !additionalPath.isBlank())
                ? cleanFileName(additionalPath) + "/" + uniqueFileName
                : uniqueFileName;

        Path root = fileConfig.getRoot();
        Path fullPath = root.resolve(relativePath).normalize();

        // Belt and braces: refuse anything that escaped the root despite the cleaning above.
        if (!fullPath.startsWith(root)) {
            throw new IllegalArgumentException("Invalid file name");
        }

        log.info("[FileHandlerUtil:saveFile] Saving file: {} to {}", uniqueFileName, fullPath);

        String fileDimension = getFileDimension(file);

        try {
            Files.createDirectories(fullPath.getParent());
            file.transferTo(fullPath.toFile());

            FileType fileType = determineFileType(originalFilename);
            log.info("[FileHandlerUtil:saveFile] File saved at: {}", fullPath);

            return new FileSaveResponse(uniqueFileName, relativePath, fileType, fileDimension);
        } catch (IOException e) {
            log.error("[FileHandlerUtil:saveFile] Error saving file: {}", e.getMessage());
            return new FileSaveResponse(uniqueFileName, relativePath, null, fileDimension);
        }
    }

    public FileType determineFileType(String fileName) {
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
        log.info("[FileHandlerUtil:determineFileType] File extension: {}", extension);
        return switch (extension) {
            case "png", "jpg", "jpeg", "gif", "bmp" -> FileType.IMAGE;
            case "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx" -> FileType.DOCUMENT;
            case "mp4", "avi", "mkv", "mov" -> FileType.VIDEO;
            case "mp3", "wav", "aac" -> FileType.AUDIO;
            default -> null;
        };
    }

    public String getFileDimension(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            return image.getWidth() + "x" + image.getHeight();
        } catch (IOException e) {
            log.error("[FileHandlerUtil:getFileDimension] Error getting file dimension: {}", e.getMessage());
            return null;
        }
    }

    public void deleteFile(String path) {
        try {
            Files.delete(Path.of(path));
            log.info("[FileHandlerUtil:deleteFile] File deleted successfully: {}", path);
        } catch (IOException e) {
            log.error("[FileHandlerUtil:deleteFile] Error deleting file: {}", e.getMessage());
        }
    }

    /**
     * Keeps only characters that are safe in a path segment, and only for known file types.
     *
     * <p>An unrecognised extension is dropped rather than trusted, so a name like
     * {@code avatar.png/../../../evil.sh} cannot steer the write.
     */
    private String cleanExtension(String extension) {
        String cleaned = extension.toLowerCase().replaceAll("[^a-z0-9]", "");
        return determineFileType("x." + cleaned) == null ? "" : cleaned;
    }

    private String cleanFileName(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "-")     // replace special characters with dash
                .replaceAll("-{2,}", "-")            // replace multiple dashes with single
                .replaceAll("^-|-$", "");            // trim leading/trailing dashes
    }

    private String getFileExtension(String fileName) {
        return fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1)
                : "";
    }

    private String getBaseName(String fileName) {
        return fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;
    }
}
