package com.atlas.search.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The filter no longer owns traceId/spanId (Micrometer Tracing does) — it only resolves a
 * {@code correlationId} and opens it as baggage. MDC population is Micrometer's job (baggage
 * correlation-fields), so these unit tests assert the filter's own responsibility: resolve
 * order (baggage -> header -> UUID), echo the response header, and always close the scope.
 */
class CorrelationIdFilterTest {

  private final Tracer tracer = mock(Tracer.class);
  private final BaggageInScope baggageInScope = mock(BaggageInScope.class);
  private final CorrelationIdFilter filter = new CorrelationIdFilter(tracer);

  @BeforeEach
  void setUp() {
    when(tracer.createBaggageInScope(eq(CorrelationIdFilter.MDC_KEY), anyString()))
        .thenReturn(baggageInScope);
  }

  @Test
  void generatesNewCorrelationId_whenHeaderAbsent() throws ServletException, IOException {
    when(tracer.getBaggage(CorrelationIdFilter.MDC_KEY)).thenReturn(null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String correlationId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId).isNotBlank();
    assertThat(UUID.fromString(correlationId)).isNotNull();
    verify(tracer).createBaggageInScope(CorrelationIdFilter.MDC_KEY, correlationId);
    verify(baggageInScope).close();
  }

  @Test
  void echoesProvidedCorrelationId_whenHeaderPresent() throws ServletException, IOException {
    when(tracer.getBaggage(CorrelationIdFilter.MDC_KEY)).thenReturn(null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "given-correlation-id");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
        .isEqualTo("given-correlation-id");
    verify(tracer).createBaggageInScope(CorrelationIdFilter.MDC_KEY, "given-correlation-id");
  }

  @Test
  void reusesPropagatedBaggage_overHeader_whenPresent() throws ServletException, IOException {
    Baggage propagated = mock(Baggage.class);
    when(propagated.get()).thenReturn("propagated-id");
    when(tracer.getBaggage(CorrelationIdFilter.MDC_KEY)).thenReturn(propagated);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "header-id"); // baggage wins
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("propagated-id");
    verify(tracer).createBaggageInScope(CorrelationIdFilter.MDC_KEY, "propagated-id");
  }

  @Test
  void generatesNewCorrelationId_whenHeaderIsBlank() throws ServletException, IOException {
    when(tracer.getBaggage(CorrelationIdFilter.MDC_KEY)).thenReturn(null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String correlationId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId).isNotBlank();
    assertThat(UUID.fromString(correlationId)).isNotNull();
  }

  @Test
  void closesBaggageScopeAndPropagatesException_whenChainThrows() {
    when(tracer.getBaggage(CorrelationIdFilter.MDC_KEY)).thenReturn(null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain throwingChain = new MockFilterChain() {
      @Override
      public void doFilter(@NonNull ServletRequest req, @NonNull ServletResponse res)
          throws IOException {
        throw new IOException("boom");
      }
    };

    assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
        .isInstanceOf(IOException.class);
    verify(baggageInScope).close();
  }
}
