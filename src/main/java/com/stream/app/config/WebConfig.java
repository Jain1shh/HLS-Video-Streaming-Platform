package com.stream.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Expose the video files (HLS output) under /files/video/ URL path
        registry.addResourceHandler("/video/**")
                .addResourceLocations("file:" + System.getProperty("user.dir") + "/video/");
    }
}