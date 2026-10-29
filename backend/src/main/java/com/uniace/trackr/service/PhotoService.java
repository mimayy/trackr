package com.uniace.trackr.service;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.PhotoEvidence;
import com.uniace.trackr.domain.enums.PhotoStatus;
import com.uniace.trackr.repo.PhotoEvidenceRepository;
import com.uniace.trackr.security.AuthPrincipal;
import com.uniace.trackr.storage.PhotoStorage;
import com.uniace.trackr.web.error.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Optional photo evidence.
 *
 * <p>Photos are always optional: the employee is offered the camera and can
 * skip it. The image goes to object storage and only its key is kept in
 * Postgres, and every read is authorised - an employee sees their own photos, a
 * supervisor sees those of the employees assigned to them, an administrator
 * sees all of them.
 */
@Service
public class PhotoService {

    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final PhotoEvidenceRepository repository;
    private final PhotoStorage storage;
    private final ScopeService scopeService;

    public PhotoService(PhotoEvidenceRepository repository, PhotoStorage storage, ScopeService scopeService) {
        this.repository = repository;
        this.storage = storage;
        this.scopeService = scopeService;
    }

    @Transactional
    public PhotoEvidence upload(Employee employee, MultipartFile file, Instant capturedAt) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("PHOTO_EMPTY", "No photo was received. Please take the photo again.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("PHOTO_TOO_LARGE",
                    "That photo is too large. Please take another one.");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest("PHOTO_TYPE_UNSUPPORTED",
                    "That file is not a photo we can accept. Please use the camera.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception ex) {
            throw ApiException.badRequest("PHOTO_UNREADABLE", "We could not read that photo. Please try again.");
        }

        LocalDate today = LocalDate.now();
        String key = "%d/%02d/%s/%s%s".formatted(
                today.getYear(), today.getMonthValue(), employee.getId(), UUID.randomUUID(), extensionFor(contentType));

        storage.store(key, content, contentType);

        PhotoEvidence photo = new PhotoEvidence();
        photo.setEmployee(employee);
        photo.setStorageKey(key);
        photo.setContentType(contentType);
        photo.setSizeBytes(content.length);
        photo.setCapturedAt(capturedAt == null ? Instant.now() : capturedAt);
        photo.setStatus(PhotoStatus.PENDING);

        return repository.save(photo);
    }

    /** Loads a photo after checking the caller is allowed to see this employee. */
    @Transactional(readOnly = true)
    public StoredPhoto download(AuthPrincipal principal, UUID photoId) {
        PhotoEvidence photo = repository.findById(photoId)
                .orElseThrow(() -> ApiException.notFound("That photo is no longer available."));

        if (photo.getStatus() == PhotoStatus.DELETED) {
            throw ApiException.notFound("That photo is no longer available.");
        }

        // Throws when out of scope, so authorisation happens before any read.
        scopeService.requireVisibleEmployee(principal, photo.getEmployee().getId());

        return new StoredPhoto(storage.read(photo.getStorageKey()), photo.getContentType());
    }

    @Transactional
    public void delete(UUID photoId) {
        repository.findById(photoId).ifPresent(photo -> {
            storage.delete(photo.getStorageKey());
            photo.setStatus(PhotoStatus.DELETED);
            repository.save(photo);
        });
    }

    /** Removes photos that were uploaded but never attached to an activity. */
    @Transactional
    public int purgeOrphans(Instant cutoff) {
        List<PhotoEvidence> orphans = repository.findByStatusAndCreatedAtBefore(PhotoStatus.PENDING, cutoff);
        orphans.forEach(photo -> {
            storage.delete(photo.getStorageKey());
            photo.setStatus(PhotoStatus.DELETED);
        });
        repository.saveAll(orphans);
        return orphans.size();
    }

    public String storageDescription() {
        return storage.describe();
    }

    public record StoredPhoto(byte[] content, String contentType) {
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
