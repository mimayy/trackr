package com.uniace.trackr.config;

import com.uniace.trackr.storage.LocalPhotoStorage;
import com.uniace.trackr.storage.PhotoStorage;
import com.uniace.trackr.storage.SupabasePhotoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public PhotoStorage photoStorage(TrackrProperties properties) {
        String provider = properties.getStorage().getProvider();

        if ("supabase".equalsIgnoreCase(provider)) {
            return new SupabasePhotoStorage(properties);
        }

        if (!"local".equalsIgnoreCase(provider)) {
            log.warn("Unknown STORAGE_PROVIDER '{}'; falling back to local development storage.", provider);
        }
        return new LocalPhotoStorage(properties);
    }
}
