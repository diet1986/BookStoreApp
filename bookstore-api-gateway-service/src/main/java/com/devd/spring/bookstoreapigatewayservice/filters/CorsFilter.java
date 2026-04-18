package com.devd.spring.bookstoreapigatewayservice.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Gateway CORS filter - runs at highest precedence before Spring Security.
 * Wraps the response to prevent downstream services from adding their own
 * Access-Control-* headers, then sets the single correct CORS header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = request.getHeader("Origin");

        if (origin != null) {
            // Handle preflight immediately
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS,PATCH");
                response.setHeader("Access-Control-Allow-Headers", "authorization,content-type,accept,origin");
                response.setHeader("Access-Control-Max-Age", "3600");
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            // Wrap response - blocks downstream CORS headers, allows our own
            BlockingCorsResponseWrapper wrapper = new BlockingCorsResponseWrapper(response, origin);
            try {
                chain.doFilter(request, wrapper);
            } finally {
                // Always set CORS headers - even on errors/exceptions
                wrapper.ensureCorsHeaders();
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private static class BlockingCorsResponseWrapper extends HttpServletResponseWrapper {

        private final String origin;
        private boolean corsHeaderSet = false;

        public BlockingCorsResponseWrapper(HttpServletResponse response, String origin) {
            super(response);
            this.origin = origin;
        }

        @Override
        public void setHeader(String name, String value) {
            if (isCorsHeader(name)) {
                // Only allow our own origin header, block wildcard
                if (!corsHeaderSet) {
                    super.setHeader("Access-Control-Allow-Origin", origin);
                    super.setHeader("Access-Control-Allow-Credentials", "true");
                    corsHeaderSet = true;
                }
            } else {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (isCorsHeader(name)) {
                if (!corsHeaderSet) {
                    super.setHeader("Access-Control-Allow-Origin", origin);
                    super.setHeader("Access-Control-Allow-Credentials", "true");
                    corsHeaderSet = true;
                }
            } else {
                super.addHeader(name, value);
            }
        }

        @Override
        public void flushBuffer() throws IOException {
            ensureCorsHeaders();
            super.flushBuffer();
        }

        private void ensureCorsHeaders() {
            if (!corsHeaderSet) {
                super.setHeader("Access-Control-Allow-Origin", origin);
                super.setHeader("Access-Control-Allow-Credentials", "true");
                corsHeaderSet = true;
            }
        }

        private boolean isCorsHeader(String name) {
            return name != null && name.toLowerCase().startsWith("access-control-");
        }
    }
}
