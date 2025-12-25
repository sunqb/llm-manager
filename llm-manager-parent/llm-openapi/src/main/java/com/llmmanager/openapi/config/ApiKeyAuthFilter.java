package com.llmmanager.openapi.config;

import com.llmmanager.service.core.entity.ApiKey;
import com.llmmanager.service.core.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import java.io.IOException;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    @Resource
    private ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        
        // Allow CORS preflight requests (OPTIONS) to pass through without checking API Key
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only protect /api/external/** and /v1/** endpoints
        if (!path.startsWith("/api/external/") && !path.startsWith("/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing or invalid Authorization header");
            return;
        }
        // 兼容Bearer 前缀，如果添加了的话，自动去除
        if (authHeader.startsWith("Bearer ")) {
            authHeader = authHeader.substring(7);
        }

        String token = authHeader;
        ApiKey apiKey = apiKeyService.findByToken(token);

        if (apiKey != null && Integer.valueOf(1).equals(apiKey.getActive())) {
            // Valid key, proceed
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Invalid or inactive API Key");
        }
    }
}
