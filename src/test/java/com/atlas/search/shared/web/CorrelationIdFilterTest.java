package com.atlas.search.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void doFilterInternal_generatesNewCorrelationAndTraceId_whenHeadersAbsent()
      throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    String correlationId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    String traceId = response.getHeader(CorrelationIdFilter.TRACE_ID_HEADER);
    Assertions.assertNotNull(correlationId);
    assertThat(UUID.fromString(correlationId)).isNotNull();
    Assertions.assertNotNull(traceId);
    assertThat(UUID.fromString(traceId)).isNotNull();
  }

  @Test
  void doFilterInternal_echoesProvidedCorrelationAndTraceId_whenHeadersPresent()
      throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "given-correlation-id");
    request.addHeader(CorrelationIdFilter.TRACE_ID_HEADER, "given-trace-id");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(
        "given-correlation-id");
    assertThat(response.getHeader(CorrelationIdFilter.TRACE_ID_HEADER)).isEqualTo("given-trace-id");
  }

  @Test
  void doFilterInternal_generatesNewCorrelationId_whenHeaderIsBlank()
      throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    String correlationId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId).isNotBlank();
    assertThat(UUID.fromString(correlationId)).isNotNull();
  }

  @Test
  void doFilterInternal_putsIdsInMdcDuringChainExecution() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "given-correlation-id");
    request.addHeader(CorrelationIdFilter.TRACE_ID_HEADER, "given-trace-id");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String[] mdcCorrelationId = new String[1];
    String[] mdcTraceId = new String[1];
    MockFilterChain chain = new MockFilterChain() {
      @Override
      public void doFilter(@NonNull ServletRequest req,
          @NonNull ServletResponse res) {
        mdcCorrelationId[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
        mdcTraceId[0] = MDC.get(CorrelationIdFilter.TRACE_ID_MDC_KEY);
      }
    };

    filter.doFilter(request, response, chain);

    assertThat(mdcCorrelationId[0]).isEqualTo("given-correlation-id");
    assertThat(mdcTraceId[0]).isEqualTo("given-trace-id");
  }

  @Test
  void doFilterInternal_clearsMdc_afterChainCompletes() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    assertThat(MDC.get(CorrelationIdFilter.TRACE_ID_MDC_KEY)).isNull();
  }

  @Test
  void doFilterInternal_clearsMdcAndPropagatesException_whenChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain() {
      @Override
      public void doFilter(@NonNull ServletRequest req, @NonNull ServletResponse res)
          throws IOException {
        throw new IOException("boom");
      }
    };

    assertThatThrownBy(() -> filter.doFilter(request, response, chain))
        .isInstanceOf(IOException.class);
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }
}
