package com.bookingsystem.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookingsystem.dto.CreateEventRequest;
import com.bookingsystem.dto.EventResponse;
import com.bookingsystem.model.Event;
import com.bookingsystem.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Create and view bookable events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @Operation(summary = "Create a new event with a fixed number of seats")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        Event event = eventService.createEvent(request.getName(), request.getTotalSeats());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(event));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch an event by ID, including current available seats")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long id) {
        Event event = eventService.getEvent(id);
        return ResponseEntity.ok(toResponse(event));
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(), event.getName(), event.getTotalSeats(), event.getAvailableSeats());
    }
}
