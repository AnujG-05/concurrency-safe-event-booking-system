package com.bookingsystem.dto;

import java.time.LocalDateTime;

import com.bookingsystem.model.BookingStatus;

public class BookingResponse {

    private Long id;
    private Long eventId;
    private Long userId;
    private int seatsBooked;
    private BookingStatus status;
    private LocalDateTime createdAt;
    private String llmExplanation;
    private Boolean explanationVerifiedAccurate;

    public BookingResponse() {
    }

    public BookingResponse(Long id,
                           Long eventId,
                           Long userId,
                           int seatsBooked,
                           BookingStatus status,
                           LocalDateTime createdAt,
                           String llmExplanation,
                           Boolean explanationVerifiedAccurate) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.seatsBooked = seatsBooked;
        this.status = status;
        this.createdAt = createdAt;
        this.llmExplanation = llmExplanation;
        this.explanationVerifiedAccurate = explanationVerifiedAccurate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public int getSeatsBooked() {
        return seatsBooked;
    }

    public void setSeatsBooked(int seatsBooked) {
        this.seatsBooked = seatsBooked;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getLlmExplanation() {
        return llmExplanation;
    }

    public void setLlmExplanation(String llmExplanation) {
        this.llmExplanation = llmExplanation;
    }

    public Boolean getExplanationVerifiedAccurate() {
        return explanationVerifiedAccurate;
    }

    public void setExplanationVerifiedAccurate(Boolean explanationVerifiedAccurate) {
        this.explanationVerifiedAccurate = explanationVerifiedAccurate;
    }
}