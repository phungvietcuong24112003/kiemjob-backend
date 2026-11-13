package com.kiemjob.backend;

import com.kiemjob.backend.security.RateLimitingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void doFilter_allowsRequestsUnderLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 15; i++) {
            filter.doFilter(request, response, filterChain);
            assertEquals(200, response.getStatus());
        }

        verify(filterChain, times(15)).doFilter(request, response);
    }

    @Test
    void doFilter_blocksRequestsExceedingLimit() throws ServletException, IOException {
        String testIp = "192.168.1.200";

        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr(testIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        // Yêu cầu thứ 16 phải bị chặn với mã 429
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/auth/login");
        blockedReq.setRemoteAddr(testIp);
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, filterChain);

        assertEquals(429, blockedRes.getStatus());
        assertEquals("60", blockedRes.getHeader("Retry-After"));
        assertTrue(blockedRes.getContentAsString().contains("Too Many Requests"));
    }

    @Test
    void doFilter_bypassesAuthMeEndpoint() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/me");
        req.setRemoteAddr("192.168.1.201");
        MockHttpServletResponse res = new MockHttpServletResponse();

        for (int i = 0; i < 25; i++) {
            filter.doFilter(req, res, filterChain);
        }

        assertEquals(200, res.getStatus());
        verify(filterChain, times(25)).doFilter(req, res);
    }
}

