package com.bookingsystem.service;

import org.springframework.stereotype.Service;

import com.bookingsystem.dto.LlmAccuracyResponse;
import com.bookingsystem.exception.BookingNotFoundException;
import com.bookingsystem.llm.LlmExplanationService;
import com.bookingsystem.llm.LlmExplanationService.ExplanationResult;
import com.bookingsystem.model.Booking;
import com.bookingsystem.model.BookingStatus;
import com.bookingsystem.repository.BookingRepository;

/**
 * Sits between BookingController and LlmExplanationService. Responsible
 * for:
 *   - only ever explaining FAILED bookings (CONFIRMED bookings don't need
 *     an explanation — there's nothing to dispute)
 *   - caching the explanation on the Booking row so the LLM is never
 *     called twice for the same booking
 *   - recording admin verification verdicts and computing the aggregate
 *     accuracy rate
 */
@Service
public class BookingExplanationService {

    private final BookingRepository bookingRepository;
    private final LlmExplanationService llmExplanationService;

    public BookingExplanationService(BookingRepository bookingRepository,
                                      LlmExplanationService llmExplanationService) {
        this.bookingRepository = bookingRepository;
        this.llmExplanationService = llmExplanationService;
    }

    public record ExplainOutcome(String explanation, boolean likelyFairOutcome, boolean wasFallback) {}

    public ExplainOutcome explainBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED bookings can be explained. This booking has status: " + booking.getStatus());
        }

        // Cache hit: don't call the LLM again for a booking we've
        // already explained.
        if (booking.getLlmExplanation() != null && !booking.getLlmExplanation().isBlank()) {
            return new ExplainOutcome(booking.getLlmExplanation(), true, false);
        }

        ExplanationResult result = llmExplanationService.explainFailedBooking(
                booking.getEventId(), booking.getUserId(), booking.getCreatedAt());

        booking.setLlmExplanation(result.explanation());
        bookingRepository.save(booking);

        return new ExplainOutcome(result.explanation(), result.likelyFairOutcome(), result.isFallback());
    }

    public void verifyExplanation(Long bookingId, boolean accurate) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        booking.setExplanationVerifiedAccurate(accurate);
        bookingRepository.save(booking);
    }

    public LlmAccuracyResponse getAccuracyStats() {
        long totalVerified = bookingRepository.findAll().stream()
                .filter(b -> b.getExplanationVerifiedAccurate() != null)
                .count();

        long markedAccurate = bookingRepository.findAll().stream()
                .filter(b -> Boolean.TRUE.equals(b.getExplanationVerifiedAccurate()))
                .count();

        double accuracyRate = totalVerified == 0 ? 0.0 : (double) markedAccurate / totalVerified;

        return new LlmAccuracyResponse(totalVerified, markedAccurate, accuracyRate);
    }
}
