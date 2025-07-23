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
import java.util.List;
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

            if (!Files.exists(inputFile)) {
                throw new RuntimeException("Input file not saved at: " + inputFile.toAbsolutePath());
            }

            // Define resolutions and settings
            String[] resolutions = {"720", "240"};
            String[] dimensions = {"1280x720", "426x240"};
            String[] bitrates = {"3000k", "800k"};

            for (int i = 0; i < resolutions.length; i++) {
                String res = resolutions[i];
                String dimension = dimensions[i];
                String bitrate = bitrates[i];
                String outputPlaylist = videoFolder.resolve(res + "p.m3u8").toString();
                String segmentPattern = videoFolder.resolve(res + "p_%03d.ts").toString();

                List<String> ffmpegCommand = List.of(
                        "ffmpeg",
                        "-i", inputFile.toAbsolutePath().toString(),
                        "-vf", "scale=" + dimension,
                        "-c:a", "aac",
                        "-ar", "48000",
                        "-c:v", "h264",
                        "-b:v", bitrate,
                        "-maxrate", calculateMaxrate(bitrate),
                        "-bufsize", calculateBufsize(bitrate),
                        "-hls_time", "10",
                        "-hls_playlist_type", "vod",
                        "-hls_segment_filename", segmentPattern,
                        outputPlaylist
                );

                System.out.println("Running FFmpeg command: " + String.join(" ", ffmpegCommand));

                ProcessBuilder pb = new ProcessBuilder(ffmpegCommand);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("FFmpeg output: " + line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("FFmpeg failed for resolution " + res + "p with exit code " + exitCode);
                }
            }

            // Write master.m3u8
            Path masterPath = videoFolder.resolve("master.m3u8");
            String masterContent =
                    "#EXTM3U\n" +
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

            // Save metadata to DB
            Video video = new Video(videoId, title, description, file.getContentType(), videoFolder.toString());
            videoRepository.save(video);

            System.out.println("Processing completed for videoId: " + videoId);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return CompletableFuture.completedFuture(null);
    }

    private String calculateMaxrate(String bitrate) {
        int br = Integer.parseInt(bitrate.replace("k", ""));
        return (int)(br * 1.07) + "k"; // ~7% overhead
    }

    private String calculateBufsize(String bitrate) {
        int br = Integer.parseInt(bitrate.replace("k", ""));
        return (int)(br * 1.5) + "k"; // buffer ~1.5x bitrate
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
