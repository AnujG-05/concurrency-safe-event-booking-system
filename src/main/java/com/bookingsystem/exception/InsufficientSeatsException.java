package com.bookingsystem.exception;

/**
 * Thrown when an event genuinely does not have enough seats left for a
 * request — this is a NORMAL business rejection, not a concurrency
 * conflict. It's important to keep this distinct from the
 * optimistic-lock-retry-exhausted case, because they have different root
 * causes and different messaging:
 *   - InsufficientSeatsException: "there simply aren't enough seats"
 *   - retry-exhausted (handled directly in BookingService, see
 *     BookingConflictException below): "we kept losing a race for the
 *     last available seats"
 */
public class InsufficientSeatsException extends RuntimeException {
    public InsufficientSeatsException(String message) {
        super(message);
    }
}
