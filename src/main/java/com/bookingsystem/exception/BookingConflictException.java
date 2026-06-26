package com.bookingsystem.exception;

/**
 * Thrown when the booking service exhausts its retry budget while
 * repeatedly losing the optimistic-locking race for the same seats.
 * This is distinct from InsufficientSeatsException: here, seats MIGHT
 * have been available at points during the attempt, but contention from
 * other concurrent requests meant we never won the race to claim them
 * within our retry budget.
 */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
