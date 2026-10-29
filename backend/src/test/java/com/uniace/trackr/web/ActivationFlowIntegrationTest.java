package com.uniace.trackr.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniace.trackr.domain.enums.AccountStatus;
import com.uniace.trackr.domain.enums.Role;
import com.uniace.trackr.support.DatabaseCleaner;
import com.uniace.trackr.support.TestFixtures;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The account lifecycle: an administrator creates an employee, the employee
 * opens the invitation link and activates, and the link then stops working.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivationFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private DatabaseCleaner cleaner;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        cleaner.clean();
        fixtures.manilaOffice();
        fixtures.standardSchedule();
        fixtures.user("admin@test.example", Role.ADMIN, "Efren P. Casingal", AccountStatus.ACTIVE);
        adminToken = bearer("admin@test.example", TestFixtures.PASSWORD);
    }

    @AfterEach
    void tearDown() {
        cleaner.clean();
    }

    @Test
    @DisplayName("a new employee starts pending, with no device registered")
    void createdEmployeeIsPending() throws Exception {
        JsonNode created = createEmployee("EMP-100", "Grace", "Fernandez", "grace@test.example");

        assertThat(created.get("summary").get("accountStatus").asText()).isEqualTo("PENDING_ACTIVATION");

        // Null fields are omitted from responses entirely, so "no device" shows
        // up as the absence of these properties rather than an explicit null.
        assertThat(created.get("summary").has("deviceStatus")).isFalse();
        assertThat(created.has("device")).isFalse();
        assertThat(created.get("invitationPending").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a pending employee cannot sign in yet, and is told why")
    void pendingEmployeeCannotSignIn() throws Exception {
        createEmployee("EMP-101", "Grace", "Fernandez", "grace@test.example");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "grace@test.example",
                                "password", "AnythingAtAll1!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_ACTIVATED"));
    }

    @Test
    @DisplayName("the invitation link shows the employee their own details before they set a password")
    void invitationDescribesTheEmployee() throws Exception {
        JsonNode created = createEmployee("EMP-102", "Grace", "Fernandez", "grace@test.example");
        String token = activationToken(created.get("summary").get("id").asText());

        mockMvc.perform(get("/api/activation/details").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Grace"))
                .andExpect(jsonPath("$.employeeNo").value("EMP-102"))
                .andExpect(jsonPath("$.branch").value("MANILA"));
    }

    @Test
    @DisplayName("activating sets the password, activates the account and signs the employee straight in")
    void activationCompletesAndSignsIn() throws Exception {
        JsonNode created = createEmployee("EMP-103", "Grace", "Fernandez", "grace@test.example");
        String token = activationToken(created.get("summary").get("id").asText());

        String body = mockMvc.perform(post("/api/activation/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", token,
                                "password", "NewEmployeePass1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.user.deviceRegistered").value(false))
                .andReturn().getResponse().getContentAsString();

        // The session returned by activation is a working one.
        String accessToken = objectMapper.readTree(body).get("accessToken").asText();
        mockMvc.perform(get("/api/me/status").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // And the new password works on the normal login screen.
        bearer("grace@test.example", "NewEmployeePass1!");
    }

    @Test
    @DisplayName("an activation link cannot be used twice")
    void activationLinkIsSingleUse() throws Exception {
        JsonNode created = createEmployee("EMP-104", "Grace", "Fernandez", "grace@test.example");
        String token = activationToken(created.get("summary").get("id").asText());

        mockMvc.perform(post("/api/activation/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", token, "password", "NewEmployeePass1!"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/activation/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", token, "password", "AnotherPassword1!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_USED"));
    }

    @Test
    @DisplayName("issuing a new invitation invalidates the previous one")
    void newInvitationInvalidatesTheOld() throws Exception {
        JsonNode created = createEmployee("EMP-105", "Grace", "Fernandez", "grace@test.example");
        String employeeId = created.get("summary").get("id").asText();

        String firstToken = activationToken(employeeId);
        String secondToken = activationToken(employeeId);

        mockMvc.perform(get("/api/activation/details").param("token", firstToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/activation/details").param("token", secondToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a made-up token is refused with a readable message")
    void unknownTokenRefused() throws Exception {
        mockMvc.perform(get("/api/activation/details").param("token", "definitely-not-a-real-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_INVALID"));
    }

    @Test
    @DisplayName("a duplicate Employee ID is refused")
    void duplicateEmployeeNumber() throws Exception {
        createEmployee("EMP-106", "Grace", "Fernandez", "grace@test.example");

        mockMvc.perform(post("/api/admin/employees")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeNo", "EMP-106",
                                "firstName", "Someone",
                                "lastName", "Else",
                                "email", "someone@test.example",
                                "branch", "MANILA",
                                "workCategory", "OFFICE_BASED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NO_TAKEN"));
    }

    /* ---------------------------------------------------------------------- */
    /* Helpers                                                                */
    /* ---------------------------------------------------------------------- */

    private JsonNode createEmployee(String employeeNo, String firstName, String lastName, String email)
            throws Exception {
        String body = mockMvc.perform(post("/api/admin/employees")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeNo", employeeNo,
                                "firstName", firstName,
                                "lastName", lastName,
                                "email", email,
                                "branch", "MANILA",
                                "workCategory", "OFFICE_BASED"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body);
    }

    /**
     * Creates an invitation and pulls the raw token out of the link. The link is
     * the only place the token ever appears - the database holds a hash of it.
     */
    private String activationToken(String employeeId) throws Exception {
        String body = mockMvc.perform(post("/api/admin/employees/" + employeeId + "/invitation")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String url = objectMapper.readTree(body).get("activationUrl").asText();
        String encoded = url.substring(url.indexOf("token=") + "token=".length());
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private String bearer(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return "Bearer " + objectMapper.readTree(body).get("accessToken").asText();
    }
}
