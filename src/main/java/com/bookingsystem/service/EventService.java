package com.bookingsystem.service;

import org.springframework.stereotype.Service;

import com.bookingsystem.exception.EventNotFoundException;
import com.bookingsystem.model.Event;
import com.bookingsystem.repository.EventRepository;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event createEvent(String name, int totalSeats) {
        Event event = new Event(name, totalSeats);
        return eventRepository.save(event);
    }

    public Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + id));
    }
}
