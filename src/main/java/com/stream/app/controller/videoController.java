package com.stream.app.controller;


import com.stream.app.entities.Video;
import com.stream.app.repositories.videoRepository;
import com.stream.app.services.videoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;


@RestController
@RequestMapping("/api/videos")
public class videoController {

    @Autowired
    private videoService videoService;

    @Autowired
    private videoRepository videoRepository;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description) {

        try {
            String videoId = videoService.startVideoProcessing(file, title, description);
            return ResponseEntity.accepted().body("Video upload started with ID: " + videoId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/stream/{videoId}")
    public ResponseEntity<Resource> streamVideo(@PathVariable String videoId) {
        Resource masterPlaylist = videoService.getVideoPlaylist(videoId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"master.m3u8\"")
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .body(masterPlaylist);
    }

    @GetMapping("/stream/{videoId}/**")
    public ResponseEntity<Resource> streamVideoFiles(
            @PathVariable String videoId,
            HttpServletRequest request) {

        // Get the path after /stream/{videoId}/
        String restOfPath = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        // Remove the /stream/{videoId}/ part
        String bestMatchPattern = (String) request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        AntPathMatcher apm = new AntPathMatcher();
        String filePath = apm.extractPathWithinPattern(bestMatchPattern, restOfPath);

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        File file = new File(video.getVideoPath(), filePath);

        if (!file.exists() || !file.isFile()) {
            throw new RuntimeException("File not found");
        }

        Resource resource = new FileSystemResource(file);

        // Set content type based on file extension
        String contentType;
        if (filePath.endsWith(".m3u8")) {
            contentType = "application/vnd.apple.mpegurl";
        } else if (filePath.endsWith(".ts")) {
            contentType = "video/MP2T";
        } else {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

}