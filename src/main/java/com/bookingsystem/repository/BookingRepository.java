package com.bookingsystem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookingsystem.model.Booking;
import com.bookingsystem.model.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEventId(Long eventId);

    long countByEventIdAndStatus(Long eventId, BookingStatus status);
}
