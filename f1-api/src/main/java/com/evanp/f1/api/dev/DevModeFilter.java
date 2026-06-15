package com.evanp.f1.api.dev;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class DevModeFilter extends OncePerRequestFilter {

    private final DevProperties devProperties;

    public DevModeFilter(DevProperties devProperties) {
        this.devProperties = devProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/dev/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!devProperties.enabled()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Dev mode is disabled");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
