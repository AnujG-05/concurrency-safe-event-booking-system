package com.bookingsystem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class CreateEventRequest {

    @NotBlank(message = "Event name is required")
    private String name;

    @Min(value = 1, message = "totalSeats must be at least 1")
    private int totalSeats;

    public CreateEventRequest() {
    }

    public CreateEventRequest(String name, int totalSeats) {
        this.name = name;
        this.totalSeats = totalSeats;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }
}