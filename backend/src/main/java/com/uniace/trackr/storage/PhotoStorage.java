package com.uniace.trackr.storage;

/**
 * Where photo evidence actually lives.
 *
 * <p>Images are never written into PostgreSQL and never served straight from
 * the backend's own disk in production - a free Render filesystem is ephemeral,
 * so a photo stored there disappears on the next deploy.
 */
public interface PhotoStorage {

    /**
     * Stores an image and returns the key used to retrieve it. The key, not the
     * image, is what goes into the database.
     */
    String store(String key, byte[] content, String contentType);

    byte[] read(String key);

    void delete(String key);

    /** Name shown on the administrator's settings screen. */
    String describe();
}
