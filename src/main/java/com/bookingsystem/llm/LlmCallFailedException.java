package com.bookingsystem.llm;

/**
 * Thrown by LlmClient whenever the LLM API call fails for any reason —
 * timeout, network error, malformed response, non-2xx status. Caught by
 * LlmExplanationService, which responds with a safe fallback message
 * instead of ever propagating this up to the client.
 */
public class LlmCallFailedException extends RuntimeException {
    public LlmCallFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
