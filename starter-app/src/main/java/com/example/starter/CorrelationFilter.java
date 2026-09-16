package com.example.starter;

import com.example.starter.platform.Ids;
import com.example.starter.platform.observability.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mints the request {@code correlation_id} at the HTTP edge and binds it in {@link LogContext} so every log
 * event in the request carries it; echoes it as the {@code X-Correlation-Id} response header, which is the
 * value a coded 500 returns as its {@code incidentId}; clears it in {@code finally}. Runs at highest
 * precedence so even a rejected request is correlated. A fresh id is always minted; a client-supplied
 * header is not trusted until there is a second deployable to propagate one from.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class CorrelationFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = Ids.newId().toString();
        LogContext.bindCorrelation(correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            LogContext.clearCorrelation();
        }
    }
}
