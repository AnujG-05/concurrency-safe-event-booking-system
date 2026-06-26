package com.bookingsystem.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookingsystem.model.BookingAttemptLog;

public interface BookingAttemptLogRepository extends JpaRepository<BookingAttemptLog, Long> {

    List<BookingAttemptLog> findByEventId(Long eventId);

    /**
     * Used by the LLM dispute resolver to pull a relevant time-windowed
     * slice of attempt logs around a failed booking, instead of dumping
     * the entire history of an event (which could be huge and irrelevant).
     */
    List<BookingAttemptLog> findByEventIdAndRequestedAtBetween(
            Long eventId, LocalDateTime start, LocalDateTime end);
}
