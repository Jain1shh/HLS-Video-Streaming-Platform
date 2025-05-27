package com.stream.app.services.impl;

import com.stream.app.entities.Video;
import com.stream.app.repositories.videoRepository;
import com.stream.app.services.videoService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class videoServiceImpl implements videoService {

    @Autowired
    private videoRepository videoRepository;

    @Value("${files.video}")
    private String videoStoragePath;

    @PostConstruct
    public void init(){
        File file = new File(videoStoragePath);

        if(!file.exists()){
            file.mkdir();
            System.out.println("Directory created");
        }else{
            System.out.println("Directory already exists");
        }
    }


    @Override
    public String startVideoProcessing(MultipartFile file, String title, String description) {
        String videoId = UUID.randomUUID().toString();

        System.out.println("Before async call");


        processVideoAsync(file, videoId, title, description);

        System.out.println("After async call - Video ID: " + videoId);



        return videoId;
    }

    @Override
    @Async
    public CompletableFuture<Void> processVideoAsync(MultipartFile file, String videoId, String title, String description) {
        System.out.println("Inside async method for videoId: " + videoId);

        try {
            String originalFilename = file.getOriginalFilename();

            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                    : "mp4";

            Path uploadRoot = Paths.get(System.getProperty("user.dir"), videoStoragePath);
            Path videoFolder = uploadRoot.resolve(videoId);
            Files.createDirectories(videoFolder);


            Path inputFile = videoFolder.resolve("input." + extension);
            file.transferTo(inputFile.toFile());

            Path outputPlaylist = videoFolder.resolve("output.m3u8");

            String command = String.format("ffmpeg -i \"%s\" -codec: copy -start_number 0 -hls_time 10 -hls_list_size 0 -f hls \"%s\"",
                    inputFile.toAbsolutePath(),
                    outputPlaylist.toAbsolutePath());

            Process process = Runtime.getRuntime().exec(command);


            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("FFmpeg stdout: " + line);
                    }
                } catch (IOException ignored) {
                }
            }).start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println("FFmpeg stderr: " + line);
                    }
                } catch (IOException ignored) {
                }
            }).start();


            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg failed with exit code " + exitCode);
            }


            Video video = new Video(videoId, title, description, file.getContentType(), videoFolder.toString());
            videoRepository.save(video);

        } catch (IOException | InterruptedException e) {

            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Resource getVideoPlaylist(String videoId) {

        Video video = videoRepository.findByVideoId(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        File playlistFile = new File(video.getVideoPath() + File.separator + "output.m3u8");
        if (!playlistFile.exists()) {
            throw new RuntimeException("Playlist not found");
        }
        return new FileSystemResource(playlistFile);
    }
}
