package com.uniace.trackr;

import com.uniace.trackr.config.TrackrProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(TrackrProperties.class)
@EnableScheduling
public class TrackrApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackrApplication.class, args);
    }
}
