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

        // Clean and generate unique file name
        String baseName = getBaseName(originalFilename);
        String extension = getFileExtension(originalFilename);
        String cleanedName = cleanFileName(baseName);
        String uniqueFileName = cleanedName + "-" + System.currentTimeMillis() + "." + extension;

        // Prepare relative and full path
        String relativePath = (additionalPath != null && !additionalPath.isBlank())
                ? additionalPath + "/" + uniqueFileName
                : uniqueFileName;
        String basePath = fileConfig.getFilePath();
        Path fullPath = Paths.get(basePath, relativePath);

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
