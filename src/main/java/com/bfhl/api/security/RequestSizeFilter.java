package com.bfhl.api.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RequestSizeFilter implements Filter {

    private static final int MAX_BYTES = 50_000; // guardrail

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;

        // Content-Length may be -1 (chunked), so we only block obvious huge ones here
        int len = req.getContentLength();
        if (len > MAX_BYTES) {
            response.setContentType("application/json");
            response.getWriter().write("{\"is_success\":false,\"official_email\":\"\",\"error\":\"Payload too large.\"}");
            ((jakarta.servlet.http.HttpServletResponse) response).setStatus(413);
            return;
        }
        chain.doFilter(request, response);
    }
}
