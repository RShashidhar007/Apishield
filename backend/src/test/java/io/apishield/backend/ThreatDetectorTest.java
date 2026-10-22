package io.apishield.backend;

import io.apishield.backend.analytics.RequestLogRepository;
import io.apishield.backend.config.AppProperties;
import io.apishield.backend.threat.SecurityEvent;
import io.apishield.backend.threat.SecurityEventRepository;
import io.apishield.backend.threat.ThreatDetector;
import io.apishield.common.model.Outcomes;
import io.apishield.common.model.TrafficEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ThreatDetectorTest {
    private final RequestLogRepository logs = mock(RequestLogRepository.class);
    private final SecurityEventRepository events = mock(SecurityEventRepository.class);
    private final ThreatDetector detector = new ThreatDetector(logs, events, new AppProperties());

    private static TrafficEvent ev(String outcome, String reason) {
        return new TrafficEvent(Instant.now(), "corr-12345678", 1L, null, null, "203.0.113.9", "GET", "/gateway/x", 401, 3, null, 0, outcome, reason);
    }

    @Test void raisesHighEventAfterThresholdOfInvalidKeys() {
        when(logs.countByClientIpAndOutcomeInAndTsAfter(eq("203.0.113.9"), any(), any())).thenReturn(5L);
        assertEquals(1, detector.evaluate(List.of(ev(Outcomes.AUTH_FAILED, "INVALID_API_KEY"), ev(Outcomes.AUTH_FAILED, "INVALID_API_KEY"))));
        ArgumentCaptor<SecurityEvent> c = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(events).save(c.capture());
        assertEquals("REPEATED_INVALID_API_KEY", c.getValue().getType()); assertEquals("HIGH", c.getValue().getSeverity());
        assertFalse(c.getValue().getDescription().contains("ask_"));
    }
    @Test void belowThresholdRaisesNothing() {
        when(logs.countByClientIpAndOutcomeInAndTsAfter(any(), any(), any())).thenReturn(2L);
        assertEquals(0, detector.evaluate(List.of(ev(Outcomes.AUTH_FAILED, "MISSING_CREDENTIALS"))));
        verify(events, never()).save(any());
    }
    @Test void doesNotRaiseDuplicateWithinWindow() {
        when(logs.countByClientIpAndOutcomeInAndTsAfter(any(), any(), any())).thenReturn(50L);
        when(events.existsByTypeAndClientIpAndApiIdAndCreatedAtAfter(any(), any(), any(), any())).thenReturn(true);
        assertEquals(0, detector.evaluate(List.of(ev(Outcomes.RATE_LIMITED, null))));
    }
    @Test void destinationViolationIsCriticalImmediately() {
        when(logs.countByClientIpAndOutcomeInAndTsAfter(any(), any(), any())).thenReturn(1L);
        detector.evaluate(List.of(ev(Outcomes.DESTINATION_BLOCKED, "DESTINATION_PRIVATE")));
        ArgumentCaptor<SecurityEvent> c = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(events).save(c.capture());
        assertEquals("CRITICAL", c.getValue().getSeverity());
    }
    @Test void successfulTrafficNeverRaisesEvents() {
        assertEquals(0, detector.evaluate(List.of(ev(Outcomes.OK, null))));
        verifyNoInteractions(events);
    }
}
