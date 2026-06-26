package com.bookingsystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bookingsystem.llm.LlmCallFailedException;
import com.bookingsystem.llm.LlmClient;
import com.bookingsystem.llm.LlmExplanationService;
import com.bookingsystem.llm.LlmExplanationService.ExplanationResult;
import com.bookingsystem.repository.BookingAttemptLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Proves the LLM explanation layer degrades gracefully under failure —
 * this is the test you'd point to in an interview to demonstrate "the
 * system stays functional even when the LLM provider is down."
 *
 * We mock LlmClient directly so this test runs instantly with no real
 * network call and no API key required.
 */
@ExtendWith(MockitoExtension.class)
class LlmExplanationFallbackTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private BookingAttemptLogRepository attemptLogRepository;

    @Test
    void whenLlmApiCallFails_fallbackMessageIsReturned() {
        when(llmClient.getCompletion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new LlmCallFailedException("simulated timeout", null));

        LlmExplanationService service = new LlmExplanationService(
                llmClient, attemptLogRepository, new ObjectMapper());

        ExplanationResult result = service.explainFailedBooking(1L, 1L, LocalDateTime.now());

        assertThat(result.isFallback()).isTrue();
        assertThat(result.explanation()).isNotBlank();
        assertThat(result.likelyFairOutcome()).isTrue();
    }

    @Test
    void whenLlmReturnsMalformedJson_fallbackMessageIsReturned() {
        when(llmClient.getCompletion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("this is not valid json at all");

        LlmExplanationService service = new LlmExplanationService(
                llmClient, attemptLogRepository, new ObjectMapper());

        ExplanationResult result = service.explainFailedBooking(1L, 1L, LocalDateTime.now());

        assertThat(result.isFallback()).isTrue();
    }

    @Test
    void whenLlmReturnsValidJsonMissingFields_fallbackMessageIsReturned() {
        // Missing "likelyFairOutcome" entirely
        when(llmClient.getCompletion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("{\"explanation\": \"some explanation\"}");

        LlmExplanationService service = new LlmExplanationService(
                llmClient, attemptLogRepository, new ObjectMapper());

        ExplanationResult result = service.explainFailedBooking(1L, 1L, LocalDateTime.now());

        assertThat(result.isFallback()).isTrue();
    }

    @Test
    void whenLlmReturnsValidJson_realExplanationIsUsed() {
        when(llmClient.getCompletion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("{\"explanation\": \"All seats were taken first.\", \"likelyFairOutcome\": true}");

        LlmExplanationService service = new LlmExplanationService(
                llmClient, attemptLogRepository, new ObjectMapper());

        ExplanationResult result = service.explainFailedBooking(1L, 1L, LocalDateTime.now());

        assertThat(result.isFallback()).isFalse();
        assertThat(result.explanation()).isEqualTo("All seats were taken first.");
        assertThat(result.likelyFairOutcome()).isTrue();
    }
}
