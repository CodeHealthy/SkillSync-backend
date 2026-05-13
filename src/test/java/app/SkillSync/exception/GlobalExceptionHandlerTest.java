package app.SkillSync.exception;

import app.SkillSync.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void handleIllegalArgumentExceptionReturnsBadRequest() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleIllegalArgumentException(
                        new IllegalArgumentException("Invalid request."),
                        request
                );

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Invalid request.", response.getBody().getMessage());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("/api/test", response.getBody().getPath());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleBadCredentialsExceptionReturnsUnauthorized() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleBadCredentialsException(
                        new BadCredentialsException("Bad credentials"),
                        request
                );

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Invalid email or password.", response.getBody().getMessage());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    void handleRuntimeExceptionReturnsBadRequest() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleRuntimeException(
                        new RuntimeException("Admin is not linked to an organization."),
                        request
                );

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Admin is not linked to an organization.", response.getBody().getMessage());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleGenericExceptionReturnsInternalServerError() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleGenericException(
                        new Exception("Unexpected failure"),
                        request
                );

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Something went wrong. Please try again later.", response.getBody().getMessage());
        assertEquals(500, response.getBody().getStatus());
    }
}