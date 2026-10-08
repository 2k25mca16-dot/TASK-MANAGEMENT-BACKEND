package com.example.demo.controller;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.model.Task;
import com.example.demo.repository.TaskRepository;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final Path fileStorageLocation;

    @Autowired
    private TaskRepository taskRepository;

    public FileController(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create the upload directory at " + this.fileStorageLocation, ex);
        }
    }

    // POST /api/files/upload
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "taskId", required = false) Long taskId) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a non-empty file to upload."));
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        // Sanitize filename
        originalFilename = originalFilename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String storedName = System.currentTimeMillis() + "_" + originalFilename;

        try {
            // Check for security directory traversal
            if (storedName.contains("..")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Filename contains invalid path sequence " + originalFilename));
            }

            Path targetLocation = this.fileStorageLocation.resolve(storedName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String formattedSize = formatFileSize(file.getSize());
            String fileUrl = "/api/files/download/" + storedName;

            // Link to Task if taskId provided
            if (taskId != null) {
                Optional<Task> opt = taskRepository.findById(taskId);
                if (opt.isPresent()) {
                    Task task = opt.get();
                    task.setHasFile(true);
                    task.setFileName(originalFilename);
                    task.setFileUrl(fileUrl);
                    task.setFileSize(formattedSize);
                    task.setFileType(file.getContentType());
                    if (task.getReviewStatus() == null || task.getReviewStatus().trim().isEmpty() || "Pending".equalsIgnoreCase(task.getReviewStatus())) {
                        task.setReviewStatus("Pending Review");
                    }
                    taskRepository.save(task);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "message", "File uploaded successfully",
                    "fileName", originalFilename,
                    "storedName", storedName,
                    "fileUrl", fileUrl,
                    "fileSize", formattedSize,
                    "fileType", file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            ));

        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Could not store file " + originalFilename + ". Please try again!"));
        }
    }

    // GET /api/files/download/{storedName:.+}
    @GetMapping("/download/{storedName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String storedName) {
        try {
            if (storedName.contains("..")) {
                return ResponseEntity.badRequest().build();
            }

            Path filePath = this.fileStorageLocation.resolve(storedName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            // Determine content type
            String contentType = null;
            try {
                contentType = Files.probeContentType(filePath);
            } catch (IOException ignored) {}

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // Extract original filename after timestamp prefix
            String displayName = storedName;
            int underscoreIdx = storedName.indexOf('_');
            if (underscoreIdx > 0 && underscoreIdx < storedName.length() - 1) {
                displayName = storedName.substring(underscoreIdx + 1);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + displayName + "\"")
                    .body(resource);

        } catch (MalformedURLException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}

