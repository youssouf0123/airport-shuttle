package com.airportshuttle;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Sends browser routes to the appropriate Ionic application entry point while
 * leaving API calls and files such as JavaScript, CSS, and images alone.
 */
@Component
public class SpaFallbackFilter extends OncePerRequestFilter {
    private static final Map<String, String> APPLICATIONS = Map.of(
            "passenger", "/passenger/index.html",
            "dispatcher", "/dispatcher/index.html",
            "driver", "/driver/index.html");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            String fallback = fallbackFor(path);
            if (fallback != null) {
                request.getRequestDispatcher(fallback).forward(request, response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String fallbackFor(String path) {
        if (path.startsWith("/api/")) return null;

        for (var application : APPLICATIONS.entrySet()) {
            String prefix = "/" + application.getKey();
            if (!path.equals(prefix) && !path.startsWith(prefix + "/")) continue;

            String remainder = path.substring(prefix.length());
            if (remainder.isEmpty() || !remainder.substring(remainder.lastIndexOf('/') + 1).contains(".")) {
                return application.getValue();
            }
        }
        return null;
    }
}
