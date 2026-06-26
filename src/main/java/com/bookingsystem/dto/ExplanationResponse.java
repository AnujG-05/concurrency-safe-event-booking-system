package com.bookingsystem.dto;

/**
 * What we return to the client from GET /api/bookings/{id}/explain.
 * Note this is OUR contract to the client — separate from the raw JSON
 * shape we ask the LLM to produce internally (see LlmRawResponse in the
 * llm package). We never pass the LLM's raw output straight through.
 */
public class ExplanationResponse {

    private Long bookingId;
    private String explanation;
    private boolean likelyFairOutcome;
    private boolean generatedByFallback;

    public ExplanationResponse() {
    }

    public ExplanationResponse(Long bookingId,
                               String explanation,
                               boolean likelyFairOutcome,
                               boolean generatedByFallback) {
        this.bookingId = bookingId;
        this.explanation = explanation;
        this.likelyFairOutcome = likelyFairOutcome;
        this.generatedByFallback = generatedByFallback;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isLikelyFairOutcome() {
        return likelyFairOutcome;
    }

    public void setLikelyFairOutcome(boolean likelyFairOutcome) {
        this.likelyFairOutcome = likelyFairOutcome;
    }

    public boolean isGeneratedByFallback() {
        return generatedByFallback;
    }

    public void setGeneratedByFallback(boolean generatedByFallback) {
        this.generatedByFallback = generatedByFallback;
    }
}