let hls;
let video = document.getElementById('video');
let qualitySelect = document.getElementById('qualitySelect');

async function loadVideo() {
    let videoId = document.getElementById('videoId').value.trim();
    if (!videoId) {
        alert("Enter a video ID!");
        return;
    }

    let masterUrl = `http://localhost:8080/api/videos/stream/${videoId}/master.m3u8`;

    if (hls) {
        hls.destroy();
    }

    if (Hls.isSupported()) {
        hls = new Hls({
            debug: true,
            startLevel: -1,
            capLevelToPlayerSize: true, // Enable adaptive sizing
            maxBufferSize: 30 * 1000 * 1000, // Increase buffer size
        });

        // Setup quality switching with improved logging
        hls.on(Hls.Events.LEVEL_SWITCHING, function(event, data) {
            console.log(`Quality switch initiated - From: ${hls.levels[hls.currentLevel]?.height}p To: ${hls.levels[data.level]?.height}p`);
            // Force clear buffer when switching
            hls.nextLoadLevel = data.level;
        });

        hls.on(Hls.Events.LEVEL_SWITCHED, function(event, data) {
            console.log(`Quality switch completed - Current: ${hls.levels[data.level].height}p`);
            // Update the select element to reflect current quality
            qualitySelect.value = data.level;
        });

        hls.on(Hls.Events.ERROR, function(event, data) {
            console.log('HLS Error:', data);
        });

        hls.loadSource(masterUrl);
        hls.attachMedia(video);

        hls.on(Hls.Events.MANIFEST_PARSED, function() {
            console.log('Available qualities:', hls.levels.map(l => `${l.height}p`));
            
            qualitySelect.innerHTML = '';

            // Add Auto option first
            let autoOption = document.createElement('option');
            autoOption.value = '-1';
            autoOption.text = 'Auto';
            qualitySelect.appendChild(autoOption);

            // Add quality options (sorted by resolution)
            hls.levels
                .sort((a, b) => b.height - a.height)
                .forEach((level, index) => {
                    let option = document.createElement('option');
                    option.value = index;
                    option.text = `${level.height}p`;
                    qualitySelect.appendChild(option);
                });

            // Set initial quality to Auto
            qualitySelect.value = '-1';

            // Handle quality selection
            qualitySelect.onchange = function() {
                let selectedLevel = parseInt(this.value);
                
                if (selectedLevel === -1) {
                    hls.currentLevel = -1;
                    hls.loadLevel = -1;
                    console.log('Switching to AUTO quality mode');
                } else {
                    // Force immediate quality switch
                    hls.nextLoadLevel = selectedLevel;
                    hls.currentLevel = selectedLevel;
                    
                    // Manual quality switch - stop automatic switching
                    hls.autoLevelEnabled = false;
                    
                    console.log(`Forcing quality switch to: ${hls.levels[selectedLevel].height}p`);
                }
            };

            video.play();
        });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = masterUrl;
        video.addEventListener('loadedmetadata', function() {
            video.play();
        });
    } else {
        alert("HLS not supported in this browser.");
    }
}
