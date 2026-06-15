package com.evanp.f1.api.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DevModeFilterTest {

    @Test
    void blocksDevRoutesWhenDisabled() throws Exception {
        DevModeFilter filter = new DevModeFilter(new DevProperties(false, "tools/track-mesh"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/dev/sessions/9161/reset");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsDevRoutesWhenEnabled() throws Exception {
        DevModeFilter filter = new DevModeFilter(new DevProperties(true, "tools/track-mesh"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/dev/sessions/9161/reset");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresNonDevRoutes() throws Exception {
        DevModeFilter filter = new DevModeFilter(new DevProperties(false, "tools/track-mesh"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions/9161");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
