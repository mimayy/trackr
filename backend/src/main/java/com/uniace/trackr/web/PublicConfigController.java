package com.uniace.trackr.web;

import com.uniace.trackr.config.TrackrProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The handful of settings the browser legitimately needs before anyone signs
 * in. Nothing secret is exposed here - no keys, no storage credentials, no
 * internal URLs.
 */
@RestController
@RequestMapping("/api/config")
public class PublicConfigController {

    private final TrackrProperties properties;

    public PublicConfigController(TrackrProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/public")
    public Map<String, Object> publicConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("appName", "Trackr@Uni-Ace");
        config.put("organisation", "Uni-Ace Adjusters Inc.");
        config.put("timezone", properties.getTimezone());
        config.put("webauthnAvailable", true);
        config.put("activationTokenHours", properties.getActivation().getTokenHours());
        return config;
    }
}
