package com.bookingsystem.dto;

import jakarta.validation.constraints.NotNull;

public class VerifyExplanationRequest {

    @NotNull(message = "accurate field is required")
    private Boolean accurate;

    public VerifyExplanationRequest() {
    }

    public VerifyExplanationRequest(Boolean accurate) {
        this.accurate = accurate;
    }

    public Boolean getAccurate() {
        return accurate;
    }

    public void setAccurate(Boolean accurate) {
        this.accurate = accurate;
    }
}