package com.bookingsystem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CreateBookingRequest {

    @NotNull(message = "eventId is required")
    private Long eventId;

    @NotNull(message = "userId is required")
    private Long userId;

    @Min(value = 1, message = "seatsBooked must be at least 1")
    private int seatsBooked;

    public CreateBookingRequest() {
    }

    public CreateBookingRequest(Long eventId, Long userId, int seatsBooked) {
        this.eventId = eventId;
        this.userId = userId;
        this.seatsBooked = seatsBooked;
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
}