package com.agriinsight.backend.shared.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesAValidatedCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "batch-2026-07-19");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("batch-2026-07-19");
        assertThat(request.getAttribute(CorrelationIdFilter.HEADER)).isEqualTo("batch-2026-07-19");
    }

    @Test
    void replacesUnsafeOrOversizedCorrelationIds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "../../secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(correlationId).isNotBlank().doesNotContain("/");
        assertThat(correlationId).isEqualTo(request.getAttribute(CorrelationIdFilter.HEADER));
    }
}
