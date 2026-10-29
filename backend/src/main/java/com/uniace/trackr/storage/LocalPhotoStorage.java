package com.uniace.trackr.storage;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.web.error.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development storage: writes photos to a folder on disk.
 *
 * <p>Fine for {@code docker compose up} on a laptop. Not for production - the
 * free hosting tiers this project targets have ephemeral filesystems, which is
 * why {@link SupabasePhotoStorage} exists.
 */
public class LocalPhotoStorage implements PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalPhotoStorage.class);

    private final Path root;

    public LocalPhotoStorage(TrackrProperties properties) {
        this.root = Paths.get(properties.getStorage().getLocalPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create the photo folder at " + root, ex);
        }
        log.info("Photo evidence will be written to {} (development storage)", root);
    }

    @Override
    public String store(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return key;
        } catch (IOException ex) {
            log.error("Could not write photo {}", key, ex);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "PHOTO_STORE_FAILED", "We could not save that photo. Please try again.");
        }
    }

    @Override
    public byte[] read(String key) {
        Path target = resolve(key);
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw ApiException.notFound("That photo is no longer available.");
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            log.warn("Could not delete photo {}", key, ex);
        }
    }

    @Override
    public String describe() {
        return "Local folder (" + root + ") - development only";
    }

    /** Guards against a crafted key escaping the photo folder. */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("INVALID_PHOTO_REFERENCE", "That photo reference is not valid.");
        }
        return target;
    }
}
