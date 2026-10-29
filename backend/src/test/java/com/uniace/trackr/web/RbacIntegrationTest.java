package com.uniace.trackr.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.Supervisor;
import com.uniace.trackr.domain.enums.AccountStatus;
import com.uniace.trackr.domain.enums.Branch;
import com.uniace.trackr.domain.enums.WorkCategory;
import com.uniace.trackr.support.DatabaseCleaner;
import com.uniace.trackr.support.TestFixtures;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Role-based access control, checked at the HTTP boundary.
 *
 * The point of these tests is that authorisation does not depend on the front
 * end hiding anything: a request crafted by hand is refused just the same.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private DatabaseCleaner cleaner;

    private Employee juan;
    private Employee otherTeamMember;
    private Supervisor maria;

    @BeforeEach
    void setUp() {
        cleaner.clean();

        fixtures.manilaOffice();
        maria = fixtures.supervisor("Maria", "Santos", "maria@test.example", Branch.MANILA);
        Supervisor ramon = fixtures.supervisor("Ramon", "Villanueva", "ramon@test.example", Branch.CEBU);

        juan = fixtures.employee("EMP-001", "Juan", "Dela Cruz", "juan@test.example",
                Branch.MANILA, WorkCategory.FIELD_BASED, maria, AccountStatus.ACTIVE);
        otherTeamMember = fixtures.employee("EMP-002", "Carlo", "Cruz", "carlo@test.example",
                Branch.CEBU, WorkCategory.FIELD_BASED, ramon, AccountStatus.ACTIVE);

        fixtures.user("admin@test.example", com.uniace.trackr.domain.enums.Role.ADMIN,
                "Efren P. Casingal", AccountStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        cleaner.clean();
    }

    @Nested
    @DisplayName("without a token")
    class Anonymous {

        @Test
        @DisplayName("protected endpoints are refused")
        void protectedEndpointsRefused() throws Exception {
            mockMvc.perform(get("/api/me/status")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/supervisor/dashboard")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/admin/employees")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("the refusal is a sentence, not a status code")
        void friendlyRefusal() throws Exception {
            mockMvc.perform(get("/api/admin/employees"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(
                            "We could not verify your session. Please log in again."));
        }

        @Test
        @DisplayName("login and public configuration stay open")
        void publicEndpointsOpen() throws Exception {
            mockMvc.perform(get("/api/config/public")).andExpect(status().isOk());
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@test.example\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("as an employee")
    class AsEmployee {

        @Test
        @DisplayName("their own workday is readable")
        void ownStatus() throws Exception {
            mockMvc.perform(get("/api/me/status").header("Authorization", bearer("juan@test.example")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("NOT_STARTED"));
        }

        @Test
        @DisplayName("the supervisor console is refused")
        void supervisorConsoleRefused() throws Exception {
            mockMvc.perform(get("/api/supervisor/dashboard")
                            .header("Authorization", bearer("juan@test.example")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("the administrator console is refused")
        void adminConsoleRefused() throws Exception {
            String token = bearer("juan@test.example");

            mockMvc.perform(get("/api/admin/employees").header("Authorization", token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/settings").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("creating an employee is refused even with a valid payload")
        void cannotCreateEmployees() throws Exception {
            mockMvc.perform(post("/api/admin/employees")
                            .header("Authorization", bearer("juan@test.example"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "employeeNo", "EMP-999",
                                    "firstName", "Sneaky",
                                    "lastName", "Hire",
                                    "email", "sneaky@test.example",
                                    "branch", "MANILA",
                                    "workCategory", "OFFICE_BASED"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("as a supervisor")
    class AsSupervisor {

        @Test
        @DisplayName("only the employees assigned to them are listed")
        void scopedEmployeeList() throws Exception {
            String body = mockMvc.perform(get("/api/supervisor/employees")
                            .header("Authorization", bearer("maria@test.example")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode rows = objectMapper.readTree(body);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("employeeNo").asText()).isEqualTo("EMP-001");
        }

        @Test
        @DisplayName("an employee from another team cannot be opened by guessing the id")
        void cannotReadOutsideScope() throws Exception {
            mockMvc.perform(get("/api/supervisor/employees/" + otherTeamMember.getId())
                            .header("Authorization", bearer("maria@test.example")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("their own team member can be opened")
        void canReadOwnTeam() throws Exception {
            mockMvc.perform(get("/api/supervisor/employees/" + juan.getId())
                            .header("Authorization", bearer("maria@test.example")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.employeeNo").value("EMP-001"));
        }

        @Test
        @DisplayName("another team's work event cannot be reviewed by guessing the id")
        void cannotReviewOutsideScope() throws Exception {
            // Scope has to hold on writes, not just on what the screens list.
            UUID eventId = fixtures.workEvent(otherTeamMember, fixtures.warehouseTwo()).getId();

            mockMvc.perform(post("/api/supervisor/work-events/" + eventId + "/review")
                            .header("Authorization", bearer("maria@test.example"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "reviewStatus", "REVIEWED_OK",
                                    "notes", "looks fine"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("administrator-only areas stay closed")
        void adminAreasClosed() throws Exception {
            String token = bearer("maria@test.example");

            mockMvc.perform(get("/api/admin/employees").header("Authorization", token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/admin/supervisors")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"ab@test.example\",\"branch\":\"MANILA\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("as an administrator")
    class AsAdministrator {

        @Test
        @DisplayName("every employee is visible")
        void seesEveryone() throws Exception {
            String body = mockMvc.perform(get("/api/admin/employees")
                            .header("Authorization", bearer("admin@test.example")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(objectMapper.readTree(body)).hasSize(2);
        }

        @Test
        @DisplayName("the supervisor console is available too, company-wide")
        void supervisorConsoleAvailable() throws Exception {
            String body = mockMvc.perform(get("/api/supervisor/employees")
                            .header("Authorization", bearer("admin@test.example")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(objectMapper.readTree(body)).hasSize(2);
        }

        @Test
        @DisplayName("audit logs and settings are readable")
        void auditAndSettings() throws Exception {
            String token = bearer("admin@test.example");

            mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/admin/settings").header("Authorization", token))
                    .andExpect(status().isOk());
        }
    }

    /* ---------------------------------------------------------------------- */
    /* Helpers                                                                */
    /* ---------------------------------------------------------------------- */

    /** Signs in for real, so the token under test is one the API actually issues. */
    private String bearer(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", TestFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return "Bearer " + objectMapper.readTree(body).get("accessToken").asText();
    }
}
