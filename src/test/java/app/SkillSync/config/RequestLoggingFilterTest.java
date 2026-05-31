package app.SkillSync.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void doFilterInternal_addsRequestIdHeaderWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        String requestId = response.getHeader("X-Request-ID");

        assertNotNull(requestId);
        assertFalse(requestId.isBlank());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_reusesProvidedRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        request.addHeader("X-Request-ID", "client-request-1");

        filter.doFilterInternal(request, response, chain);

        assertEquals("client-request-1", response.getHeader("X-Request-ID"));
        verify(chain).doFilter(request, response);
    }
}
