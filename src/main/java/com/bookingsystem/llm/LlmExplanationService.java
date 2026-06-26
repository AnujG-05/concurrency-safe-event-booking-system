package com.bookingsystem.llm;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bookingsystem.model.BookingAttemptLog;
import com.bookingsystem.repository.BookingAttemptLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * THE MOST IMPORTANT FILE TO UNDERSTAND IN THE LLM LAYER.
 *
 * This service explains WHY a booking failed, using an LLM, while
 * following one hard rule throughout: the LLM's output is never trusted
 * blindly, and its unavailability never breaks anything.
 *
 * The flow:
 *   1. Gather structured, non-PII context about the failed booking
 *      (timestamps, retry counts, outcome strings — NEVER raw user data
 *      like names or emails) from BookingAttemptLog.
 *   2. Ask the LLM to explain it, demanding a strict JSON shape.
 *   3. Attempt to parse that JSON into LlmRawResponse.
 *   4. If ANYTHING goes wrong at any step — the API call fails, the
 *      response isn't valid JSON, the JSON is missing fields — we catch
 *      it and return a generic, deterministic fallback message instead.
 *      The client never sees a raw/broken LLM response, and the system
 *      never goes down because the LLM provider had a bad day.
 *
 * Why the LLM is never allowed to influence the ACTUAL booking decision:
 *   - Determinism: the booking outcome must be reproducible and
 *     explainable purely from our own business rules (seat counts,
 *     locking). If an LLM call influenced whether a seat was granted,
 *     the same input could produce different outcomes on different days
 *     depending on model behavior — unacceptable for a system handling
 *     real reservations.
 *   - Latency: LLM calls take hundreds of milliseconds to seconds. The
 *     booking path needs to stay fast and short-lived to minimize the
 *     window during which we hold any kind of contention.
 *   - Auditability: if a booking is ever disputed, we need to be able to
 *     point to exact, deterministic rule logic ("seats were 0 at the time
 *     of this UPDATE") rather than "the model decided this was fine."
 *     The LLM here only narrates a decision that ALREADY happened.
 */
@Service
public class LlmExplanationService {

    private static final Logger log = LoggerFactory.getLogger(LlmExplanationService.class);

    private static final String SYSTEM_PROMPT = """
            You are explaining why a seat-booking attempt failed on a
            ticketing platform. You will be given a structured summary of
            booking attempt logs around the time of the failure.

            Respond ONLY with a single JSON object in this EXACT shape,
            with no markdown formatting, no code fences, and no extra
            commentary before or after it:

            {"explanation": "<2-3 plain English sentences>", "likelyFairOutcome": true or false}

            Rules:
            - "explanation" must be understandable to a non-technical user.
            - "likelyFairOutcome" should be true if the failure looks like
              a normal capacity/race-condition outcome, and false if the
              data suggests something unusual (e.g. excessive retries
              that still failed, or a suspicious pattern) worth a human
              looking into.
            - Do not invent details not present in the data provided.
            """;

    private final LlmClient llmClient;
    private final BookingAttemptLogRepository attemptLogRepository;
    private final ObjectMapper objectMapper;

    public LlmExplanationService(LlmClient llmClient,
                                  BookingAttemptLogRepository attemptLogRepository,
                                  ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.attemptLogRepository = attemptLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Result wrapper distinguishing a genuine LLM explanation from our
     * safe fallback, so the controller/cache layer can record which one
     * was actually used.
     */
    public record ExplanationResult(String explanation, boolean likelyFairOutcome, boolean isFallback) {}

    public ExplanationResult explainFailedBooking(Long eventId, Long userId, LocalDateTime failedAt) {
        String context = buildContextSummary(eventId, failedAt);

        try {
            String rawJson = llmClient.getCompletion(SYSTEM_PROMPT, context);
            LlmRawResponse parsed = parseStrict(rawJson);

            if (parsed != null && parsed.isValid()) {
                return new ExplanationResult(
                        parsed.getExplanation(),
                        parsed.getLikelyFairOutcome(),
                        false
                );
            }

            log.warn("LLM response failed validation, using fallback. Raw response: {}", rawJson);
            return fallback();

        } catch (LlmCallFailedException ex) {
            log.warn("LLM call failed, using fallback. Reason: {}", ex.getMessage());
            return fallback();
        } catch (Exception ex) {
            // Belt-and-suspenders: ANY unexpected error here must still
            // result in a safe fallback, never a broken API response.
            log.error("Unexpected error generating LLM explanation, using fallback", ex);
            return fallback();
        }
    }

    /**
     * Attempts to parse the LLM's raw text as JSON matching our strict
     * contract. Returns null (rather than throwing) if parsing fails, so
     * the caller can cleanly fall back without a try-catch around every
     * call site.
     */
    private LlmRawResponse parseStrict(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            // Defensive trim in case the model wraps output in code
            // fences despite instructions not to.
            String cleaned = rawJson.trim()
                    .replaceAll("^```json", "")
                    .replaceAll("^```", "")
                    .replaceAll("```$", "")
                    .trim();
            return objectMapper.readValue(cleaned, LlmRawResponse.class);
        } catch (Exception ex) {
            log.warn("Failed to parse LLM response as JSON: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * The safe, deterministic message used whenever the LLM can't be
     * trusted for any reason. This is what guarantees the system stays
     * fully functional even with zero LLM availability.
     */
    private ExplanationResult fallback() {
        return new ExplanationResult(
                "Your booking failed because all available seats were claimed "
                        + "before your request could be completed. This commonly "
                        + "happens when many people try to book the same event at "
                        + "the same time.",
                true,
                true
        );
    }

    /**
     * Builds a structured, PII-free summary of attempt logs around the
     * failure for the LLM to reason over. Only IDs, counts and outcome
     * strings are included — never names, emails, or any personal data.
     */
    private String buildContextSummary(Long eventId, LocalDateTime failedAt) {
        LocalDateTime windowStart = failedAt.minusMinutes(2);
        LocalDateTime windowEnd = failedAt.plusMinutes(2);

        List<BookingAttemptLog> logs = attemptLogRepository
                .findByEventIdAndRequestedAtBetween(eventId, windowStart, windowEnd);

        String logLines = logs.stream()
                .map(l -> String.format(
                        "- outcome=%s, retryCount=%d, errorReason=%s, requestedAt=%s",
                        l.getOutcome(), l.getRetryCount(), l.getErrorReason(), l.getRequestedAt()))
                .collect(Collectors.joining("\n"));

        return String.format(
                "Event ID: %d%nTotal attempts in this 4-minute window: %d%nAttempt log entries:%n%s",
                eventId, logs.size(), logLines.isBlank() ? "(no entries found)" : logLines
        );
    }
}
