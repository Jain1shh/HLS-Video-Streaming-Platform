package com.stream.app.controller;


import com.stream.app.services.videoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/videos")
public class videoController {

    @Autowired
    private videoService videoService;

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
        Resource playlist = videoService.getVideoPlaylist(videoId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + playlist.getFilename() + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .body(playlist);
    }
}