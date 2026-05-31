package app.SkillSync.dto;

import app.SkillSync.model.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthResponseTest {

    @Test
    void authResponse_whenSerialized_doesNotExposeJwtToken() throws Exception {
        AuthResponse response = new AuthResponse(
                "jwt-token",
                "user-1",
                "Jane Recruiter",
                "jane@skillsync.com",
                Role.RECRUITER
        );

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(response));

        assertFalse(json.has("token"));
    }
}
