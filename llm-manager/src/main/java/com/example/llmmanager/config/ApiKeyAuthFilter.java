package com.example.llmmanager.config;

import com.example.llmmanager.entity.ApiKey;
import com.example.llmmanager.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        
        // Allow CORS preflight requests (OPTIONS) to pass through without checking API Key
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only protect /api/external/** endpoints
        if (!path.startsWith("/api/external/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        Optional<ApiKey> keyOpt = apiKeyRepository.findByToken(token);

        if (keyOpt.isPresent() && keyOpt.get().isActive()) {
            // Valid key, proceed
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Invalid or inactive API Key");
        }
    }
}
