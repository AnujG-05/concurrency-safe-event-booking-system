package com.bookingsystem.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookingsystem.dto.LlmAccuracyResponse;
import com.bookingsystem.service.BookingExplanationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Operational reporting endpoints")
public class AdminController {

    private final BookingExplanationService bookingExplanationService;

    public AdminController(BookingExplanationService bookingExplanationService) {
        this.bookingExplanationService = bookingExplanationService;
    }

    @GetMapping("/llm-accuracy")
    @Operation(summary = "Reports what fraction of LLM-generated booking explanations "
            + "were verified as accurate by an admin reviewer")
    public ResponseEntity<LlmAccuracyResponse> getLlmAccuracy() {
        return ResponseEntity.ok(bookingExplanationService.getAccuracyStats());
    }
}
