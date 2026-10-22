package io.apishield.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs against H2 (MSSQL mode) with Hibernate-generated schema. Native T-SQL queries and Flyway are NOT exercised here. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AuthFlowIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper om;

    private String json(String email, String pw) { return "{\"email\":\"" + email + "\",\"displayName\":\"Dev\",\"password\":\"" + pw + "\"}"; }
    private JsonNode login(String email, String pw) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(email, pw))).andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString());
    }

    @Test void registerLoginMeAndNeverLeakHashes() throws Exception {
        MvcResult reg = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json("dev1@example.com", "S3cure-passw0rd!"))).andExpect(status().isCreated()).andReturn();
        assertFalse(reg.getResponse().getContentAsString().toLowerCase().contains("password"));
        JsonNode t = login("dev1@example.com", "S3cure-passw0rd!");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + t.get("accessToken").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("dev1@example.com"));
    }
    @Test void invalidCredentialsReturn401WithStableCode() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json("nobody@example.com", "wrong-password-1")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
    @Test void protectedEndpointsRequireTokenAndGarbageTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/apis")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/apis").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }
    @Test void developerCannotAdministerOrManage() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json("dev2@example.com", "S3cure-passw0rd!"))).andExpect(status().isCreated());
        String tok = login("dev2@example.com", "S3cure-passw0rd!").get("accessToken").asText();
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + tok)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").header("Authorization", "Bearer " + tok)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/security/events").header("Authorization", "Bearer " + tok)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/analytics/summary").header("Authorization", "Bearer " + tok)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/apis").header("Authorization", "Bearer " + tok).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
    @Test void accountLocksAfterRepeatedFailures() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json("dev3@example.com", "S3cure-passw0rd!"))).andExpect(status().isCreated());
        for (int i = 0; i < 3; i++) mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json("dev3@example.com", "bad-password-123"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json("dev3@example.com", "S3cure-passw0rd!"))).andExpect(status().isLocked());
    }
    @Test void refreshTokenRotatesAndReplayRevokesFamily() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json("dev4@example.com", "S3cure-passw0rd!"))).andExpect(status().isCreated());
        String r1 = login("dev4@example.com", "S3cure-passw0rd!").get("refreshToken").asText();
        MvcResult ok = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + r1 + "\"}")).andExpect(status().isOk()).andReturn();
        String r2 = om.readTree(ok.getResponse().getContentAsString()).get("refreshToken").asText();
        assertNotEquals(r1, r2);
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + r1 + "\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + r2 + "\"}")).andExpect(status().isUnauthorized());
    }
    @Test void internalEndpointsRejectMissingOrWrongGatewayToken() throws Exception {
        mvc.perform(get("/internal/v1/gateway/config")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/gateway/config").header("X-Gateway-Token", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/gateway/config").header("X-Gateway-Token", "test-gateway-token-test-gateway-token")).andExpect(status().isOk());
    }
    @Test void userJwtCannotCallInternalEndpoints() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json("dev5@example.com", "S3cure-passw0rd!"))).andExpect(status().isCreated());
        String tok = login("dev5@example.com", "S3cure-passw0rd!").get("accessToken").asText();
        mvc.perform(get("/internal/v1/gateway/config").header("Authorization", "Bearer " + tok)).andExpect(status().isUnauthorized());
    }
}
