# HLS Video Streaming Platform

Video streaming platform with HLS processing using Java 21, Spring Boot 3, and FFmpeg.

# FFmpeg HLS Processing Guide

## What is HLS?

**HTTP Live Streaming (HLS)** is an adaptive bitrate streaming protocol developed by Apple. It breaks video files into small segments and creates a playlist file that allows video players to stream content efficiently.

### Why HLS?

- **Adaptive Streaming**: Automatically adjusts quality based on network conditions
- **Universal Compatibility**: Supported by all modern browsers and devices
- **Efficient Delivery**: Segments can be cached and delivered via CDN
- **Seamless Playback**: No buffering interruptions during quality changes
- **HTTP-based**: Works through firewalls and standard web infrastructure

## How FFmpeg Processes HLS(without adaptive bit rates)

to convert videos to HLS format:

```bash
ffmpeg -i "input_video.mp4" -codec: copy -start_number 0 -hls_time 10 -hls_list_size 0 -f hls "output_playlist.m3u8"
```

## Adaptive bit rates

For adaptive bitrate streaming with multiple qualities (which we have implemented):
```bash
# Multiple bitrate example (more complex)
ffmpeg -i input.mp4 \
  -map 0:v -map 0:a -map 0:v -map 0:a \
  -c:v:0 libx264 -b:v:0 2M -s:v:0 1280x720 \
  -c:v:1 libx264 -b:v:1 500k -s:v:1 640x360 \
  -c:a copy \
  -f hls -hls_time 10 -hls_list_size 0 \
  -master_pl_name master.m3u8 \
  -var_stream_map "v:0,a:0 v:1,a:1" output_%v.m3u8
```

### Command Breakdown

| Parameter | Description |
|-----------|-------------|
| `-i "input_video.mp4"` | Input video file path |
| `-codec: copy` | Copy video/audio streams without re-encoding (faster processing) |
| `-start_number 0` | Start segment numbering from 0 |
| `-hls_time 10` | Each segment duration is 10 seconds |
| `-hls_list_size 0` | Keep all segments in playlist (0 = unlimited) |
| `-f hls` | Output format is HLS |
| `"output_playlist.m3u8"` | Output playlist file name |

## Processing Steps

### 1. **Segmentation**
FFmpeg splits the input video into 10-second segments:
```
video_segment_0.ts
video_segment_1.ts
video_segment_2.ts
...
```

### 2. **Playlist Creation**
Creates an M3U8 playlist file containing:
```m3u8
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:10
#EXT-X-MEDIA-SEQUENCE:0
#EXTINF:10.0,
video_segment_0.ts
#EXTINF:10.0,
video_segment_1.ts
#EXTINF:10.0,
video_segment_2.ts
#EXT-X-ENDLIST
```

### 3. **Stream Copy Benefits**
Using `-codec: copy` means:
- **No Quality Loss**: Original video/audio quality preserved
- **Fast Processing**: No re-encoding, just segmentation
- **Resource Efficient**: Minimal CPU usage
- **Quick Turnaround**: Processing completes in seconds vs minutes

## File Structure After Processing(without adaptive bit rates)

```
output_folder/
├── playlist.m3u8          # Main playlist file
├── video_segment_0.ts     # First 10-second segment
├── video_segment_1.ts     # Second 10-second segment
├── video_segment_2.ts     # Third 10-second segment
└── ...                    # Additional segments
```

## How Video Players Use HLS

1. **Load Playlist**: Player fetches the `.m3u8` file
2. **Parse Segments**: Reads segment list and durations
3. **Sequential Download**: Downloads segments in order
4. **Buffering**: Maintains buffer of upcoming segments
5. **Playback**: Plays segments seamlessly while downloading next ones

## Advantages of This Approach

### **Speed**
- No transcoding = faster processing
- 10-second segments = quick initial load

### **Compatibility**
- Works with original video codec
- Standard HLS format supported everywhere

### **Simplicity**
- Single quality stream (no adaptive bitrate complexity)
- Straightforward implementation

## Limitations

- **No Quality Adaptation**: Single bitrate only
- **Codec Dependency**: Output quality depends on input codec
- **File Size**: Large input files create large segments

## Technical Details

### **Transport Stream (.ts)**
- Container format for video segments
- Designed for streaming applications
- Resilient to transmission errors
- Self-contained segments

### **M3U8 Playlist**
- UTF-8 encoded M3U playlist
- Contains metadata and segment information
- Updated dynamically for live streams
- Static for video-on-demand (VOD)

This approach provides efficient, fast HLS conversion suitable for most video streaming applications.


## Prerequisites

- Java 21
- Maven 3.6+
- FFmpeg installed and in PATH

## Setup

1. Clone repository

2. Install FFmpeg:
   - Ubuntu: `sudo apt install ffmpeg`
   - macOS: `brew install ffmpeg`
   - Windows: Download from ffmpeg.org and add to PATH

3. Req. setup of MySQL Db and it's config in application.properties file

4. Run:
```bash
mvn spring-boot:run
```

## API Endpoints

### Upload Video
```http
POST /api/videos/upload
Content-Type: multipart/form-data
Body: 
- file (video file)
- title (string)
- description (string)
```

### Stream Video
Returns the main M3U8 playlist file that video players use to initiate streaming. This endpoint sets the correct MIME type (application/vnd.apple.mpegurl) so browsers know how to handle the response.
```http
GET /api/videos/stream/{videoId}
```

### Segment Streaming Endpoint
This is where the magic happens. The wildcard pattern captures requests for individual video segments (.ts files) and additional playlist files.
```http
GET /api/videos/stream/{videoId}/**
```

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
