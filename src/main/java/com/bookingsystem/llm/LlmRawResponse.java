package com.bookingsystem.llm;

/**
 * The EXACT shape we instruct the LLM to respond in. This class exists
 * purely so Jackson can attempt to deserialize the LLM's raw text output
 * into something structured.
 *
 * IMPORTANT: we never trust that the LLM actually obeyed our instructions.
 * LlmExplanationService wraps every deserialization attempt in a
 * try-catch and falls back to a safe templated message if:
 *   - the LLM didn't return valid JSON at all
 *   - the JSON is valid but missing one of these fields
 *   - the API call itself failed/timed out
 *
 * This class has no business logic — it's a pure data carrier.
 */
public class LlmRawResponse {

    private String explanation;
    private Boolean likelyFairOutcome;

    public LlmRawResponse() {
        // required by Jackson for deserialization
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Boolean getLikelyFairOutcome() {
        return likelyFairOutcome;
    }

    public void setLikelyFairOutcome(Boolean likelyFairOutcome) {
        this.likelyFairOutcome = likelyFairOutcome;
    }

    /**
     * Both fields must be present and non-null for this response to be
     * considered valid. This is the gate that decides whether we trust
     * the LLM's output or fall back to our safe default.
     */
    public boolean isValid() {
        return explanation != null && !explanation.isBlank() && likelyFairOutcome != null;
    }
}
