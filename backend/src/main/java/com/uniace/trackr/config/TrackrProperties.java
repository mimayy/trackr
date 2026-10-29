package com.uniace.trackr.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable value in the application, bound from configuration and
 * therefore from environment variables. Nothing here is hard-coded at a call
 * site, so the same build runs locally and in production.
 */
@ConfigurationProperties(prefix = "trackr")
public class TrackrProperties {

    private String timezone = "Asia/Manila";
    private String appBaseUrl = "http://localhost:5173";
    private String frontendUrls = "http://localhost:5173";
    private String backendUrl = "http://localhost:8080";

    private final Security security = new Security();
    private final WebAuthn webauthn = new WebAuthn();
    private final Activation activation = new Activation();
    private final Gps gps = new Gps();
    private final Attendance attendance = new Attendance();
    private final Sync sync = new Sync();
    private final Storage storage = new Storage();
    private final Email email = new Email();
    private final Retention retention = new Retention();
    private final Demo demo = new Demo();

    /** Allowed browser origins, parsed from the comma separated FRONTEND_URL. */
    public List<String> allowedOrigins() {
        List<String> out = new ArrayList<>();
        for (String part : frontendUrls.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getAppBaseUrl() { return appBaseUrl; }
    public void setAppBaseUrl(String appBaseUrl) { this.appBaseUrl = appBaseUrl; }

    public String getFrontendUrls() { return frontendUrls; }
    public void setFrontendUrls(String frontendUrls) { this.frontendUrls = frontendUrls; }

    public String getBackendUrl() { return backendUrl; }
    public void setBackendUrl(String backendUrl) { this.backendUrl = backendUrl; }

    public Security getSecurity() { return security; }
    public WebAuthn getWebauthn() { return webauthn; }
    public Activation getActivation() { return activation; }
    public Gps getGps() { return gps; }
    public Attendance getAttendance() { return attendance; }
    public Sync getSync() { return sync; }
    public Storage getStorage() { return storage; }
    public Email getEmail() { return email; }
    public Retention getRetention() { return retention; }
    public Demo getDemo() { return demo; }

    public static class Security {
        private String jwtSecret;
        private int accessTokenMinutes = 30;
        private int refreshTokenDays = 14;
        private String bootstrapAdminEmail = "admin@uni-ace.example";
        private String bootstrapAdminPassword = "ChangeMe123!";
        private int authRateLimitPerMinute = 20;

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

        public int getAccessTokenMinutes() { return accessTokenMinutes; }
        public void setAccessTokenMinutes(int accessTokenMinutes) { this.accessTokenMinutes = accessTokenMinutes; }

        public int getRefreshTokenDays() { return refreshTokenDays; }
        public void setRefreshTokenDays(int refreshTokenDays) { this.refreshTokenDays = refreshTokenDays; }

        public String getBootstrapAdminEmail() { return bootstrapAdminEmail; }
        public void setBootstrapAdminEmail(String bootstrapAdminEmail) { this.bootstrapAdminEmail = bootstrapAdminEmail; }

        public String getBootstrapAdminPassword() { return bootstrapAdminPassword; }
        public void setBootstrapAdminPassword(String bootstrapAdminPassword) { this.bootstrapAdminPassword = bootstrapAdminPassword; }

        public int getAuthRateLimitPerMinute() { return authRateLimitPerMinute; }
        public void setAuthRateLimitPerMinute(int authRateLimitPerMinute) { this.authRateLimitPerMinute = authRateLimitPerMinute; }
    }

    public static class WebAuthn {
        private String rpId = "localhost";
        private String rpName = "Trackr@Uni-Ace";
        private String origins = "http://localhost:5173";
        private int challengeTtlSeconds = 300;

        public List<String> originList() {
            List<String> out = new ArrayList<>();
            for (String part : origins.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
            return out;
        }

        public String getRpId() { return rpId; }
        public void setRpId(String rpId) { this.rpId = rpId; }

        public String getRpName() { return rpName; }
        public void setRpName(String rpName) { this.rpName = rpName; }

        public String getOrigins() { return origins; }
        public void setOrigins(String origins) { this.origins = origins; }

        public int getChallengeTtlSeconds() { return challengeTtlSeconds; }
        public void setChallengeTtlSeconds(int challengeTtlSeconds) { this.challengeTtlSeconds = challengeTtlSeconds; }
    }

    public static class Activation {
        private int tokenHours = 48;

        public int getTokenHours() { return tokenHours; }
        public void setTokenHours(int tokenHours) { this.tokenHours = tokenHours; }
    }

    public static class Gps {
        private double maxAccuracyMeters = 150;
        private double warnAccuracyMeters = 50;
        private double locationMatchSlackMeters = 250;

        public double getMaxAccuracyMeters() { return maxAccuracyMeters; }
        public void setMaxAccuracyMeters(double maxAccuracyMeters) { this.maxAccuracyMeters = maxAccuracyMeters; }

        public double getWarnAccuracyMeters() { return warnAccuracyMeters; }
        public void setWarnAccuracyMeters(double warnAccuracyMeters) { this.warnAccuracyMeters = warnAccuracyMeters; }


        public double getLocationMatchSlackMeters() { return locationMatchSlackMeters; }
        public void setLocationMatchSlackMeters(double v) { this.locationMatchSlackMeters = v; }
    }

    public static class Attendance {
        private int lateGraceMinutes = 0;
        private int overtimeThresholdMinutes = 30;
        private int maxWorkdayHours = 16;

        public int getLateGraceMinutes() { return lateGraceMinutes; }
        public void setLateGraceMinutes(int lateGraceMinutes) { this.lateGraceMinutes = lateGraceMinutes; }

        public int getOvertimeThresholdMinutes() { return overtimeThresholdMinutes; }
        public void setOvertimeThresholdMinutes(int v) { this.overtimeThresholdMinutes = v; }

        public int getMaxWorkdayHours() { return maxWorkdayHours; }
        public void setMaxWorkdayHours(int maxWorkdayHours) { this.maxWorkdayHours = maxWorkdayHours; }
    }

    public static class Sync {
        private int maxClockSkewMinutes = 10;

        public int getMaxClockSkewMinutes() { return maxClockSkewMinutes; }
        public void setMaxClockSkewMinutes(int v) { this.maxClockSkewMinutes = v; }
    }

    public static class Storage {
        private String provider = "local";
        private String localPath = "./data/photos";
        private String url = "";
        private String bucket = "trackr-photos";
        private String key = "";
        private String secret = "";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }

    public static class Email {
        private String provider = "none";
        private String apiKey = "";
        private String from = "Trackr@Uni-Ace <no-reply@uni-ace.example>";

        public boolean isEnabled() {
            return !"none".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank();
        }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
    }

    public static class Retention {
        private int workEventDays = 1095;
        private int photoDays = 365;
        private int auditLogDays = 1095;

        public int getWorkEventDays() { return workEventDays; }
        public void setWorkEventDays(int workEventDays) { this.workEventDays = workEventDays; }

        public int getPhotoDays() { return photoDays; }
        public void setPhotoDays(int photoDays) { this.photoDays = photoDays; }

        public int getAuditLogDays() { return auditLogDays; }
        public void setAuditLogDays(int auditLogDays) { this.auditLogDays = auditLogDays; }
    }

    public static class Demo {
        private boolean seed = false;
        private String password = "Trackr123!";

        public boolean isSeed() { return seed; }
        public void setSeed(boolean seed) { this.seed = seed; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
