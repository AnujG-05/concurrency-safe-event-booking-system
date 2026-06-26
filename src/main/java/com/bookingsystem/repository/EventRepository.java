package com.bookingsystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookingsystem.model.Event;

public interface EventRepository extends JpaRepository<Event, Long> {
    // No custom queries needed yet — findById/save from JpaRepository
    // are all the booking logic requires. Hibernate automatically applies
    // optimistic-lock checking on every save() because of the @Version
    // field on Event — we don't need to write any extra code for that.
}
