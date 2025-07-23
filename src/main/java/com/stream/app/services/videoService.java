package com.stream.app.services;

import com.stream.app.entities.Video;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface videoService {


    CompletableFuture<Void> processVideoAsync(MultipartFile file, String videoId, String title, String description) throws IOException;
    Resource getVideoPlaylist(String videoId);
    String startVideoProcessing(MultipartFile file, String title, String description);
}