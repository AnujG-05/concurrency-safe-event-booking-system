package com.bookingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Concurrency-Safe Event Booking System.
 *
 * This application demonstrates three things end-to-end:
 *   1. A real race condition in seat booking, proven by a concurrent test
 *   2. A fix using JPA optimistic locking (@Version) with bounded retries
 *   3. An LLM integrated as a SECONDARY explainer for failed bookings —
 *      it never decides anything; it only explains decisions the
 *      deterministic service layer already made.
 */
@SpringBootApplication
public class BookingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingSystemApplication.class, args);
    }
}
