package com.airportshuttle;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SpaFallbackFilterTest {
    private final SpaFallbackFilter filter = new SpaFallbackFilter();

    @Test
    void forwardsNewRoutesToTheMatchingApplication() throws Exception {
        assertForwarded("/passenger/bookings", "/passenger/index.html");
        assertForwarded("/dispatcher/settings/notifications", "/dispatcher/index.html");
        assertForwarded("/driver/routes/today", "/driver/index.html");
    }

    @Test
    void leavesApiCallsAndStaticAssetsUntouched() throws Exception {
        assertPassedThrough("/api/auth/me");
        assertPassedThrough("/passenger/main.js");
        assertPassedThrough("/dispatcher/assets/logo.svg");
    }

    private void assertForwarded(String path, String expected) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        var response = new MockHttpServletResponse();
        var chainCalled = new AtomicBoolean();
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertEquals(expected, response.getForwardedUrl());
        assertFalse(chainCalled.get());
    }

    private void assertPassedThrough(String path) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        var response = new MockHttpServletResponse();
        var chainCalled = new AtomicBoolean();
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(chainCalled.get());
        assertNull(response.getForwardedUrl());
    }
}
