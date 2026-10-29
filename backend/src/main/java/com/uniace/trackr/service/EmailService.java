package com.uniace.trackr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniace.trackr.config.TrackrProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Optional invitation email.
 *
 * <p>Email is never a dependency of activation. The administrator can always
 * copy the activation link from the screen, which is what makes this deployable
 * for free. If a provider is configured, sending is attempted as a convenience
 * and a failure is reported without blocking anything.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final TrackrProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public EmailService(TrackrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.getEmail().isEnabled();
    }

    /** @return a short note about what happened, for the administrator's screen */
    public SendOutcome sendInvitation(String toEmail, String firstName, String activationUrl, long expiryHours) {
        if (!isEnabled()) {
            return new SendOutcome(false,
                    "Email is not configured. Use \"Copy activation link\" and send it to the employee yourself.");
        }

        String subject = "Your Trackr@Uni-Ace account is ready";
        String body = """
                Hi %s,

                Your Uni-Ace account has been created.

                Open this link on your phone to activate it and set up your fingerprint:

                %s

                This link works once and expires in %d hours.

                - Uni-Ace Adjusters Inc.
                """.formatted(firstName, activationUrl, expiryHours);

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "from", properties.getEmail().getFrom(),
                    "to", new String[]{toEmail},
                    "subject", subject,
                    "text", body));

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + properties.getEmail().getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return new SendOutcome(true, "Invitation emailed to " + toEmail + ".");
            }

            log.warn("Invitation email rejected: {} {}", response.statusCode(), response.body());
            return new SendOutcome(false,
                    "The email could not be sent. Use \"Copy activation link\" instead.");

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new SendOutcome(false, "The email could not be sent. Use \"Copy activation link\" instead.");
        } catch (Exception ex) {
            log.warn("Could not send invitation email", ex);
            return new SendOutcome(false, "The email could not be sent. Use \"Copy activation link\" instead.");
        }
    }

    public record SendOutcome(boolean sent, String note) {
    }
}
