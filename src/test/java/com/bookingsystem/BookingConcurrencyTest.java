package com.bookingsystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.bookingsystem.model.Booking;
import com.bookingsystem.model.BookingStatus;
import com.bookingsystem.model.Event;
import com.bookingsystem.repository.BookingRepository;
import com.bookingsystem.repository.EventRepository;
import com.bookingsystem.service.BookingService;

/**
 * THE CENTERPIECE TEST OF THIS ENTIRE PROJECT.
 *
 * This test proves, with real numbers, that the optimistic-locking +
 * retry mechanism in BookingService correctly prevents overbooking under
 * genuine concurrent load.
 *
 * ============================================================
 * THE STORY THIS TEST TELLS
 * ============================================================
 * We create an event with exactly 5 seats. We then fire 20 threads at
 * the EXACT same instant, each trying to book exactly 1 seat. Without
 * any concurrency protection (see the long comment block in
 * BookingService.java describing the "naive version"), this scenario
 * would produce MORE than 5 confirmed bookings, because multiple threads
 * could read the same stale "seats available" value before any of them
 * had written back their change.
 *
 * With optimistic locking + retry in place, this test asserts that:
 *   1. EXACTLY 5 bookings end up CONFIRMED — never more.
 *   2. The other 15 attempts end up FAILED.
 *   3. The event's final availableSeats is exactly 0 — never negative.
 *
 * ============================================================
 * HOW THE TEST FORCES TRUE SIMULTANEITY
 * ============================================================
 * It's not enough to just loop and call the method 20 times sequentially
 * — that would never reproduce the race, since each call would complete
 * before the next one starts. Instead we use:
 *   - An ExecutorService with a fixed pool of 20 threads, so all 20
 *     requests genuinely run in parallel, not queued one after another.
 *   - A CountDownLatch ("startGate") that EVERY thread waits on before
 *     calling placeBooking(). Each thread reaches the gate and blocks;
 *     only when all 20 threads are ready and the main test thread calls
 *     startGate.countDown() do they all proceed at (as close to) the
 *     same instant as the JVM thread scheduler allows.
 *   - A second CountDownLatch ("doneGate") that each thread counts down
 *     when it finishes, so the main test thread can wait for all 20 to
 *     complete before checking the final numbers.
 */
@SpringBootTest
class BookingConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void concurrentBookings_neverExceedAvailableSeats() throws InterruptedException {
        // ---- ARRANGE ----
        final int totalSeats = 5;
        final int numberOfConcurrentRequests = 20;

        Event event = eventRepository.save(new Event("Concurrency Test Concert", totalSeats));
        Long eventId = event.getId();

        ExecutorService executor = Executors.newFixedThreadPool(numberOfConcurrentRequests);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(numberOfConcurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // ---- ACT ----
        for (int i = 0; i < numberOfConcurrentRequests; i++) {
            final long userId = i + 1; // simulate 20 distinct users
            executor.submit(() -> {
                try {
                    startGate.await(); // every thread blocks here until released together
                    bookingService.placeBooking(eventId, userId, 1);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    // Expected for the 15 requests that lose the race —
                    // InsufficientSeatsException or BookingConflictException
                    failureCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown(); // release all 20 threads at once
        boolean completedInTime = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // ---- ASSERT ----
        assertThat(completedInTime)
                .as("All 20 concurrent booking attempts should finish within 30 seconds")
                .isTrue();

        Event finalEvent = eventRepository.findById(eventId).orElseThrow();
        List<Booking> allBookings = bookingRepository.findByEventId(eventId);

        long confirmedCount = allBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .count();
        long failedCount = allBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.FAILED)
                .count();

        // ---- PRINT THE BEFORE/AFTER STORY ----
        System.out.println("================================================");
        System.out.println("CONCURRENCY TEST RESULTS");
        System.out.println("================================================");
        System.out.println("Total seats available at start:      " + totalSeats);
        System.out.println("Concurrent booking attempts fired:   " + numberOfConcurrentRequests);
        System.out.println("------------------------------------------------");
        System.out.println("Confirmed bookings (expected = 5):   " + confirmedCount);
        System.out.println("Failed bookings    (expected = 15):  " + failedCount);
        System.out.println("Final availableSeats (expected = 0): " + finalEvent.getAvailableSeats());
        System.out.println("------------------------------------------------");
        System.out.println("WITHOUT optimistic locking, this exact scenario would");
        System.out.println("typically produce MORE than 5 confirmed bookings and/or");
        System.out.println("a negative availableSeats value, because multiple threads");
        System.out.println("could read the same stale seat count before any of them");
        System.out.println("committed their write. See BookingService.java for the");
        System.out.println("full explanation of the naive/broken version.");
        System.out.println("================================================");

        // The actual proof: never more confirmed bookings than seats existed,
        // and seats never go negative.
        assertThat(confirmedCount)
                .as("Exactly %d bookings should be confirmed for %d available seats", totalSeats, totalSeats)
                .isEqualTo(totalSeats);

        assertThat(failedCount)
                .as("The remaining attempts should all be cleanly rejected, not silently succeed")
                .isEqualTo(numberOfConcurrentRequests - totalSeats);

        assertThat(finalEvent.getAvailableSeats())
                .as("availableSeats must never go negative — that would mean overbooking occurred")
                .isEqualTo(0);
    }
}
