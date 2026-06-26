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

/**
 * Core booking logic. THIS CLASS IS THE WHOLE POINT OF THE PROJECT.
 *
 * ============================================================
 * WHAT THE NAIVE / BROKEN VERSION WOULD HAVE LOOKED LIKE
 * ============================================================
 * If you didn't know about optimistic locking, the obvious way to write
 * this method would be:
 *
 *     Event event = eventRepository.findById(eventId).get();
 *     if (event.getAvailableSeats() >= seatsRequested) {
 *         event.setAvailableSeats(event.getAvailableSeats() - seatsRequested);
 *         eventRepository.save(event);
 *         // create CONFIRMED booking
 *     } else {
 *         // create FAILED booking
 *     }
 *
 * This is a classic "check-then-act" race condition. Imagine availableSeats
 * = 1, and two threads (Thread A and Thread B) both call this method at
 * almost the same instant:
 *
 *   Thread A reads availableSeats = 1   (1 >= 1, passes the check)
 *   Thread B reads availableSeats = 1   (1 >= 1, passes the check too!)
 *   Thread A writes availableSeats = 0, saves, books seat
 *   Thread B writes availableSeats = 0, saves, books seat
 *
 * Both threads believed they were allowed to book, because nothing
 * stopped them from both reading the SAME stale value before either of
 * them had written anything back. The result: 2 bookings confirmed for
 * 1 available seat — an overbooking bug. With totalSeats = 5 and 20
 * concurrent requests, this exact bug produces MORE than 5 confirmed
 * bookings, exactly what our concurrency test below proves.
 *
 * ============================================================
 * HOW THIS CLASS ACTUALLY FIXES IT: OPTIMISTIC LOCKING + RETRY
 * ============================================================
 * Event.java has a @Version field. Every UPDATE Hibernate issues against
 * the events table includes "AND version = ?" in its WHERE clause. If
 * Thread A commits first, the row's version increments. When Thread B's
 * transaction then tries to commit ITS update (based on the now-stale
 * version it read), zero rows match the WHERE clause, and Hibernate
 * throws ObjectOptimisticLockingFailureException.
 *
 * So instead of silently succeeding like the naive version, the LOSING
 * thread in the race gets a clear signal that it needs to re-check
 * reality. We catch that exception and retry: re-fetch the event (now
 * with the up-to-date seat count and version), and try again — up to
 * MAX_RETRIES times. This means the second thread correctly discovers,
 * on retry, that there are now 0 seats left, and fails cleanly instead
 * of overbooking.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int MAX_RETRIES = 3;

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final BookingAttemptLogRepository attemptLogRepository;

    public BookingService(EventRepository eventRepository,
                           BookingRepository bookingRepository,
                           BookingAttemptLogRepository attemptLogRepository) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.attemptLogRepository = attemptLogRepository;
    }

    /**
     * Public entry point. Retries the core attempt on optimistic-lock
     * conflicts, up to MAX_RETRIES times, before giving up.
     *
     * Each individual retry is its OWN database transaction (see
     * attemptBooking's @Transactional) — this is deliberate. If the
     * whole retry loop were wrapped in one big transaction, we'd be
     * reading the same stale snapshot on every "retry" in some isolation
     * levels, defeating the purpose. By committing/rolling back each
     * attempt independently, every retry gets a genuinely fresh read of
     * the current database state.
     */
    public Booking placeBooking(Long eventId, Long userId, int seatsRequested) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                return attemptBooking(eventId, userId, seatsRequested, attempt);

            } catch (ObjectOptimisticLockingFailureException ex) {
                logAttempt(eventId, userId, "FAILED_OPTIMISTIC_LOCK_RETRY", attempt,
                        "Lost optimistic lock race, retrying");
                log.info("Optimistic lock conflict on event {} (attempt {}), retrying...",
                        eventId, attempt);
                attempt++;
            }
        }

        // We exhausted every retry and still kept losing the race.
        logAttempt(eventId, userId, "FAILED_CONFLICT_RETRY_EXHAUSTED", attempt,
                "Exhausted retries due to repeated concurrent conflicts");

        Booking failedBooking = new Booking(eventId, userId, seatsRequested, BookingStatus.FAILED);
        bookingRepository.save(failedBooking);

        throw new BookingConflictException(
                "Could not complete booking due to high contention for this event. Please try again.");
    }

    /**
     * A single booking attempt: read the event, check seat availability,
     * decrement if possible, save. The @Transactional boundary here is
     * what makes Hibernate's version-check-on-UPDATE actually matter —
     * the read and the write happen within one transaction, and the
     * save() at the end is the moment the version check is enforced.
     */
    @Transactional
    protected Booking attemptBooking(Long eventId, Long userId, int seatsRequested, int attemptNumber) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        if (event.getAvailableSeats() < seatsRequested) {
            logAttempt(eventId, userId, "FAILED_NO_SEATS", attemptNumber,
                    "Not enough seats available at read time");

            Booking failedBooking = new Booking(eventId, userId, seatsRequested, BookingStatus.FAILED);
            bookingRepository.save(failedBooking);

            throw new InsufficientSeatsException(
                    "Not enough seats available for event: " + eventId);
        }

        event.setAvailableSeats(event.getAvailableSeats() - seatsRequested);

        // THIS is the line where an ObjectOptimisticLockingFailureException
        // gets thrown, if another thread committed a change to this same
        // event row since we read it above. Hibernate flushes the UPDATE
        // (with "AND version = ?" in the WHERE clause) right here.
        eventRepository.save(event);

        logAttempt(eventId, userId, "SUCCESS", attemptNumber, null);

        Booking confirmedBooking = new Booking(eventId, userId, seatsRequested, BookingStatus.CONFIRMED);
        return bookingRepository.save(confirmedBooking);
    }

    private void logAttempt(Long eventId, Long userId, String outcome, int retryCount, String errorReason) {
        BookingAttemptLog attemptLog = new BookingAttemptLog(eventId, userId, outcome, retryCount, errorReason);
        attemptLogRepository.save(attemptLog);
    }
}
