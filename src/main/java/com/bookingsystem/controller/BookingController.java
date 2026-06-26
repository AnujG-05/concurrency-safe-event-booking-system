package com.bookingsystem.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookingsystem.dto.BookingResponse;
import com.bookingsystem.dto.CreateBookingRequest;
import com.bookingsystem.dto.ExplanationResponse;
import com.bookingsystem.dto.VerifyExplanationRequest;
import com.bookingsystem.exception.BookingNotFoundException;
import com.bookingsystem.model.Booking;
import com.bookingsystem.repository.BookingRepository;
import com.bookingsystem.service.BookingExplanationService;
import com.bookingsystem.service.BookingExplanationService.ExplainOutcome;
import com.bookingsystem.service.BookingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Place bookings and inspect/explain their outcomes")
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final BookingExplanationService bookingExplanationService;

    public BookingController(BookingService bookingService,
                              BookingRepository bookingRepository,
                              BookingExplanationService bookingExplanationService) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.bookingExplanationService = bookingExplanationService;
    }

    @PostMapping
    @Operation(summary = "Attempt to book seats for an event (optimistic-lock protected)")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        Booking booking = bookingService.placeBooking(
                request.getEventId(), request.getUserId(), request.getSeatsBooked());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(booking));
    }

    @GetMapping("/event/{eventId}")
    @Operation(summary = "List all bookings (confirmed and failed) for a given event")
    public ResponseEntity<List<BookingResponse>> getBookingsForEvent(@PathVariable Long eventId) {
        List<BookingResponse> responses = bookingRepository.findByEventId(eventId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single booking by ID")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + id));
        return ResponseEntity.ok(toResponse(booking));
    }

    @GetMapping("/{id}/explain")
    @Operation(summary = "Get an LLM-generated explanation for why a FAILED booking didn't succeed. "
            + "Falls back to a safe generic message if the LLM is unavailable or returns an invalid response.")
    public ResponseEntity<ExplanationResponse> explainBooking(@PathVariable Long id) {
        ExplainOutcome outcome = bookingExplanationService.explainBooking(id);
        return ResponseEntity.ok(new ExplanationResponse(
                id, outcome.explanation(), outcome.likelyFairOutcome(), outcome.wasFallback()));
    }

    @PatchMapping("/{id}/verify-explanation")
    @Operation(summary = "Admin endpoint: mark whether the LLM's explanation for this booking was accurate")
    public ResponseEntity<Void> verifyExplanation(@PathVariable Long id,
                                                   @Valid @RequestBody VerifyExplanationRequest request) {
        bookingExplanationService.verifyExplanation(id, request.getAccurate());
        return ResponseEntity.noContent().build();
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getEventId(),
                booking.getUserId(),
                booking.getSeatsBooked(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getLlmExplanation(),
                booking.getExplanationVerifiedAccurate()
        );
    }
}
