package com.paytm.seat_reservation.logging;

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
import java.util.regex.Pattern;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestedId = request.getHeader("X-Request-ID");
        String requestId = requestedId != null && REQUEST_ID_PATTERN.matcher(requestedId).matches()
                ? requestedId
                : UUID.randomUUID().toString();
        long startedAt = System.nanoTime();

        response.setHeader("X-Request-ID", requestId);
        MDC.put("request_id", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            logger.atInfo()
                    .addKeyValue("event", "http_request_completed")
                    .addKeyValue("http_method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("duration_ms", durationMs)
                    .log("http_request_completed");
            MDC.remove("request_id");
        }
    }
}