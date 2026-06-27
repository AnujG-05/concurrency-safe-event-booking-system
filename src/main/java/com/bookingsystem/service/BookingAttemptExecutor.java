package com.bookingsystem.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingsystem.exception.EventNotFoundException;
import com.bookingsystem.exception.InsufficientSeatsException;
import com.bookingsystem.model.Booking;
import com.bookingsystem.model.BookingAttemptLog;
import com.bookingsystem.model.BookingStatus;
import com.bookingsystem.model.Event;
import com.bookingsystem.repository.BookingAttemptLogRepository;
import com.bookingsystem.repository.BookingRepository;
import com.bookingsystem.repository.EventRepository;

@Service
public class BookingAttemptExecutor {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final BookingAttemptLogRepository attemptLogRepository;

    public BookingAttemptExecutor(EventRepository eventRepository,
                                  BookingRepository bookingRepository,
                                  BookingAttemptLogRepository attemptLogRepository) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.attemptLogRepository = attemptLogRepository;
    }

    @Transactional
    public Booking attemptBooking(Long eventId,
                                  Long userId,
                                  int seatsRequested,
                                  int attemptNumber) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new EventNotFoundException("Event not found: " + eventId));

        if (event.getAvailableSeats() < seatsRequested) {

            logAttempt(
                    eventId,
                    userId,
                    "FAILED_NO_SEATS",
                    attemptNumber,
                    "Not enough seats available"
            );

            Booking failedBooking =
                    new Booking(eventId, userId, seatsRequested, BookingStatus.FAILED);

            bookingRepository.save(failedBooking);

            throw new InsufficientSeatsException(
                    "Not enough seats available for event: " + eventId
            );
        }

        event.setAvailableSeats(
                event.getAvailableSeats() - seatsRequested
        );

        
        eventRepository.saveAndFlush(event);

        logAttempt(
                eventId,
                userId,
                "SUCCESS",
                attemptNumber,
                null
        );

        Booking confirmedBooking =
                new Booking(eventId, userId, seatsRequested, BookingStatus.CONFIRMED);

        return bookingRepository.save(confirmedBooking);
    }

    private void logAttempt(Long eventId,
                            Long userId,
                            String outcome,
                            int retryCount,
                            String errorReason) {

        BookingAttemptLog attemptLog =
                new BookingAttemptLog(
                        eventId,
                        userId,
                        outcome,
                        retryCount,
                        errorReason
                );

        attemptLogRepository.save(attemptLog);
    }
}
