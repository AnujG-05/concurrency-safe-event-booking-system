package com.bookingsystem.model;

/**
 * Final outcome of a booking attempt.
 *
 * CONFIRMED  - seats were successfully reserved
 * FAILED     - the attempt did not result in a reservation, for any reason
 *              (no seats available, OR optimistic-lock retries exhausted).
 *              We deliberately don't split this into more granular enum
 *              values on the Booking itself — the FINER-GRAINED reason
 *              lives in BookingAttemptLog.outcome instead, since a single
 *              Booking can be preceded by multiple internal attempts
 *              (retries) before reaching its final status.
 */
public enum BookingStatus {
    CONFIRMED,
    FAILED
}
