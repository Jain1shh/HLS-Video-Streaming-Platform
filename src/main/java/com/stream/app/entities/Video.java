package com.stream.app.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Video {

    public Video() {
    }

    public Video(String videoId, String videoTitle, String videoDescription, String contentType, String videoPath) {
        this.videoId = videoId;
        this.videoTitle = videoTitle;
        this.videoDescription = videoDescription;
        this.contentType = contentType;
        this.videoPath = videoPath;
    }

    @Id
    private String videoId;
    private String videoTitle;
    private String videoDescription;
    private String contentType;
    private String videoPath;

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public String getVideoDescription() {
        return videoDescription;
    }

    public void setVideoDescription(String videoDescription) {
        this.videoDescription = videoDescription;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }
}
