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

            // Define output filenames
            Path masterPlaylist = videoFolder.resolve("master.m3u8");

            // FFmpeg command for adaptive streaming
            String command = String.format(
                    "ffmpeg -i \"%s\" " +
                            "-filter:v:0 scale=w=1920:h=1080 -c:a aac -ar 48000 -c:v:0 h264 -b:v:0 5000k -maxrate:v:0 5350k -bufsize:v:0 7500k -hls_time 10 -hls_playlist_type vod -hls_segment_filename \"%s/1080p_%%03d.ts\" \"%s/1080p.m3u8\" " +
                            "-filter:v:1 scale=w=1280:h=720 -c:a aac -ar 48000 -c:v:1 h264 -b:v:1 3000k -maxrate:v:1 3210k -bufsize:v:1 4200k -hls_time 10 -hls_playlist_type vod -hls_segment_filename \"%s/720p_%%03d.ts\" \"%s/720p.m3u8\" " +
                            "-filter:v:2 scale=w=854:h=480 -c:a aac -ar 48000 -c:v:2 h264 -b:v:2 1500k -maxrate:v:2 1605k -bufsize:v:2 2100k -hls_time 10 -hls_playlist_type vod -hls_segment_filename \"%s/480p_%%03d.ts\" \"%s/480p.m3u8\" " +
                            "-filter:v:3 scale=w=426:h=240 -c:a aac -ar 48000 -c:v:3 h264 -b:v:3 800k -maxrate:v:3 856k -bufsize:v:3 1200k -hls_time 10 -hls_playlist_type vod -hls_segment_filename \"%s/240p_%%03d.ts\" \"%s/240p.m3u8\"",
                    inputFile.toAbsolutePath(),
                    videoFolder.toAbsolutePath(), videoFolder.toAbsolutePath(),
                    videoFolder.toAbsolutePath(), videoFolder.toAbsolutePath(),
                    videoFolder.toAbsolutePath(), videoFolder.toAbsolutePath(),
                    videoFolder.toAbsolutePath(), videoFolder.toAbsolutePath()
            );

            System.out.println("Running FFmpeg command: " + command);

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

            // Generate master.m3u8
            Path masterPath = videoFolder.resolve("master.m3u8");
            String masterContent = "#EXTM3U\n" +
                    "#EXT-X-VERSION:3\n" +
                    "#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080\n" +
                    "1080p.m3u8\n" +
                    "#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720\n" +
                    "720p.m3u8\n" +
                    "#EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=854x480\n" +
                    "480p.m3u8\n" +
                    "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=426x240\n" +
                    "240p.m3u8\n";

            Files.writeString(masterPath, masterContent);

            Video video = new Video(videoId, title, description, file.getContentType(), videoFolder.toString());
            videoRepository.save(video);

            System.out.println("Processing completed for videoId: " + videoId);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Resource getVideoPlaylist(String videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        File masterPlaylistFile = new File(video.getVideoPath() + File.separator + "master.m3u8");
        if (!masterPlaylistFile.exists()) {
            throw new RuntimeException("Master playlist not found");
        }
        return new FileSystemResource(masterPlaylistFile);
    }

}
