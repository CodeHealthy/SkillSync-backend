package app.SkillSync.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = getOrCreateRequestId(request);
        long startedAt = System.currentTimeMillis();

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
            logRequest(request, response, requestId, startedAt, null);
        } catch (ServletException | IOException | RuntimeException exception) {
            logRequest(request, response, requestId, startedAt, exception);
            throw exception;
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private String getOrCreateRequestId(HttpServletRequest request) {
        String providedRequestId = request.getHeader(REQUEST_ID_HEADER);

        if (providedRequestId != null && !providedRequestId.isBlank()) {
            return providedRequestId.trim();
        }

        return UUID.randomUUID().toString();
    }

    private void logRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            String requestId,
            long startedAt,
            Exception exception
    ) {
        long durationMs = System.currentTimeMillis() - startedAt;

        if (exception == null) {
            log.info(
                    "HTTP request completed: requestId={}, method={}, path={}, status={}, durationMs={}",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs
            );
            return;
        }

        log.warn(
                "HTTP request failed: requestId={}, method={}, path={}, status={}, durationMs={}, exception={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                durationMs,
                exception.getClass().getSimpleName()
        );
    }
}
