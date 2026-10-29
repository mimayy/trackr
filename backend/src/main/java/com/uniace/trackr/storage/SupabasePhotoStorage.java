package com.uniace.trackr.storage;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.web.error.ApiException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Production storage: Supabase Storage, which has a free tier large enough for
 * a student project.
 *
 * <p>The bucket must be <b>private</b>. Photos are fetched with the service key
 * from the backend and streamed to the caller only after Trackr has checked
 * that this particular person is allowed to see them - a public bucket would
 * make every photo readable by anyone holding the URL.
 */
public class SupabasePhotoStorage implements PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabasePhotoStorage.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;
    private final String bucket;
    private final String serviceKey;

    public SupabasePhotoStorage(TrackrProperties properties) {
        TrackrProperties.Storage storage = properties.getStorage();
        if (storage.getUrl() == null || storage.getUrl().isBlank()
                || storage.getKey() == null || storage.getKey().isBlank()) {
            throw new IllegalStateException(
                    "STORAGE_PROVIDER=supabase requires STORAGE_URL and STORAGE_KEY to be set.");
        }
        this.baseUrl = storage.getUrl().replaceAll("/+$", "");
        this.bucket = storage.getBucket();
        this.serviceKey = storage.getKey();
        log.info("Photo evidence will be stored in the Supabase bucket '{}'", bucket);
    }

    @Override
    public String store(String key, byte[] content, String contentType) {
        HttpRequest request = authorised(objectUri(key))
                .header("Content-Type", contentType)
                .header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();

        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString(), "upload");
        if (response.statusCode() / 100 != 2) {
            log.error("Supabase rejected a photo upload: {} {}", response.statusCode(), response.body());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_STORE_FAILED",
                    "We could not save that photo. Please try again.");
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        HttpRequest request = authorised(objectUri(key)).GET().build();
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray(), "download");

        if (response.statusCode() == 404) {
            throw ApiException.notFound("That photo is no longer available.");
        }
        if (response.statusCode() / 100 != 2) {
            log.error("Supabase rejected a photo download: {}", response.statusCode());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PHOTO_READ_FAILED",
                    "We could not load that photo right now. Please try again.");
        }
        return response.body();
    }

    @Override
    public void delete(String key) {
        HttpRequest request = authorised(objectUri(key)).DELETE().build();
        try {
            send(request, HttpResponse.BodyHandlers.ofString(), "delete");
        } catch (RuntimeException ex) {
            log.warn("Could not delete photo {} from Supabase", key, ex);
        }
    }

    @Override
    public String describe() {
        return "Supabase Storage (private bucket '" + bucket + "')";
    }

    private URI objectUri(String key) {
        return URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + key);
    }

    private HttpRequest.Builder authorised(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + serviceKey)
                .header("apikey", serviceKey);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler, String operation) {
        try {
            return client.send(request, handler);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PHOTO_STORAGE_UNAVAILABLE",
                    "Photo storage is not responding. Please try again in a moment.");
        } catch (Exception ex) {
            log.error("Supabase photo {} failed", operation, ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PHOTO_STORAGE_UNAVAILABLE",
                    "Photo storage is not responding. Please try again in a moment.");
        }
    }
}
