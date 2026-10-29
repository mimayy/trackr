package com.uniace.trackr.service;

import com.uniace.trackr.config.TrackrProperties;
import com.uniace.trackr.repo.RefreshTokenRepository;
import com.uniace.trackr.repo.WebAuthnChallengeRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Housekeeping: expired tokens, spent challenges and photos that were uploaded
 * but never attached to an activity.
 *
 * <p>Work events and audit logs are deliberately <b>not</b> deleted here.
 * Retention periods for them are configurable and documented, but removing
 * attendance history is a decision for Uni-Ace to make explicitly, not
 * something a background job should do on its own.
 */
@Service
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final WebAuthnChallengeRepository challengeRepository;
    private final PhotoService photoService;
    private final TrackrProperties properties;

    public RetentionJob(RefreshTokenRepository refreshTokenRepository,
                        WebAuthnChallengeRepository challengeRepository,
                        PhotoService photoService,
                        TrackrProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.challengeRepository = challengeRepository;
        this.photoService = photoService;
        this.properties = properties;
    }

    /** Runs hourly. Cheap enough to be harmless on a free instance. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    @Transactional
    public void cleanUp() {
        try {
            Instant now = Instant.now();

            int tokens = refreshTokenRepository.deleteExpired(now);
            int challenges = challengeRepository.deleteExpired(now);
            int photos = photoService.purgeOrphans(now.minus(Duration.ofHours(24)));

            if (tokens + challenges + photos > 0) {
                log.info("Housekeeping removed {} expired sessions, {} spent challenges and {} unused photos",
                        tokens, challenges, photos);
            }
        } catch (RuntimeException ex) {
            log.warn("Housekeeping run failed; it will be retried on the next schedule", ex);
        }
    }

    public int photoRetentionDays() {
        return properties.getRetention().getPhotoDays();
    }
}
