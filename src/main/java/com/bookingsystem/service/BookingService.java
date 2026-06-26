package com.bookingsystem.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingsystem.exception.BookingConflictException;
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
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int MAX_RETRIES = 3;

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final BookingAttemptLogRepository attemptLogRepository;
    private final BookingAttemptExecutor bookingAttemptExecutor;

   public BookingService(EventRepository eventRepository,
                      BookingRepository bookingRepository,
                      BookingAttemptLogRepository attemptLogRepository,
                      BookingAttemptExecutor bookingAttemptExecutor) {

    this.eventRepository = eventRepository;
    this.bookingRepository = bookingRepository;
    this.attemptLogRepository = attemptLogRepository;
    this.bookingAttemptExecutor = bookingAttemptExecutor;
}

   
    public Booking placeBooking(Long eventId, Long userId, int seatsRequested) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
               return bookingAttemptExecutor.attemptBooking(
        eventId,
        userId,
        seatsRequested,
        attempt
        );

            } catch (ObjectOptimisticLockingFailureException ex) {
                logAttempt(eventId, userId, "FAILED_OPTIMISTIC_LOCK_RETRY", attempt,
                        "Lost optimistic lock race, retrying");
                log.info("Optimistic lock conflict on event {} (attempt {}), retrying...",
                        eventId, attempt);
                attempt++;
            }
        }

        logAttempt(eventId, userId, "FAILED_CONFLICT_RETRY_EXHAUSTED", attempt,
                "Exhausted retries due to repeated concurrent conflicts");

        Booking failedBooking = new Booking(eventId, userId, seatsRequested, BookingStatus.FAILED);
        bookingRepository.save(failedBooking);

        throw new BookingConflictException(
                "Could not complete booking due to high contention for this event. Please try again.");
    }

    
    private void logAttempt(Long eventId, Long userId, String outcome, int retryCount, String errorReason) {
        BookingAttemptLog attemptLog = new BookingAttemptLog(eventId, userId, outcome, retryCount, errorReason);
        attemptLogRepository.save(attemptLog);
    }
}
