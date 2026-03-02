package com.geomerge.controller;

import com.geomerge.exception.PdfMergeException;
import com.geomerge.service.PdfMergeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;

@RestController
@RequestMapping("/api")
public class MergeController {

    private static final Logger log = LoggerFactory.getLogger(MergeController.class);

    private final PdfMergeService pdfMergeService;

    public MergeController(PdfMergeService pdfMergeService) {
        this.pdfMergeService = pdfMergeService;
    }

    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> mergePdfs(
            @RequestParam("file1") MultipartFile file1,
            @RequestParam("file2") MultipartFile file2) throws Exception {

        log.info("Merge request received: file1='{}' ({}), file2='{}' ({})",
                file1.getOriginalFilename(), formatSize(file1.getSize()),
                file2.getOriginalFilename(), formatSize(file2.getSize()));

        byte[] mergedPdf = pdfMergeService.merge(
                file1.getInputStream(), file2.getInputStream(),
                file1.getOriginalFilename(), file2.getOriginalFilename());

        log.info("Merge complete. Output size: {}", formatSize(mergedPdf.length));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"merged.pdf\"")
                .body(mergedPdf);
    }

    @PostMapping(value = "/merge-urls", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<byte[]> mergePdfsByUrl(
            @RequestParam("url1") String url1,
            @RequestParam("url2") String url2) throws Exception {

        log.info("URL merge request: url1='{}', url2='{}'", url1, url2);

        // Extract a readable name from the URL path (e.g. "export.pdf" or the last segment)
        String name1 = extractNameFromUrl(url1);
        String name2 = extractNameFromUrl(url2);

        try (
                InputStream stream1 = URI.create(url1).toURL().openStream();
                InputStream stream2 = URI.create(url2).toURL().openStream()
        ) {
            byte[] mergedPdf = pdfMergeService.merge(stream1, stream2, name1, name2);

            log.info("URL merge complete. Output size: {}", formatSize(mergedPdf.length));

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"merged.pdf\"")
                    .body(mergedPdf);
        } catch (java.net.MalformedURLException e) {
            throw new PdfMergeException("Invalid URL: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new PdfMergeException("Failed to download PDF: " + e.getMessage(), e);
        }
    }

    private String extractNameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null && !path.isEmpty()) {
                String lastSegment = path.substring(path.lastIndexOf('/') + 1);
                if (!lastSegment.isEmpty()) return lastSegment;
            }
        } catch (Exception ignored) {}
        return url;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
