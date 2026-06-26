package com.bookingsystem.dto;

public class LlmAccuracyResponse {

    private long totalVerified;
    private long markedAccurate;
    private double accuracyRate;

    public LlmAccuracyResponse() {
    }

    public LlmAccuracyResponse(long totalVerified,
                               long markedAccurate,
                               double accuracyRate) {
        this.totalVerified = totalVerified;
        this.markedAccurate = markedAccurate;
        this.accuracyRate = accuracyRate;
    }

    public long getTotalVerified() {
        return totalVerified;
    }

    public void setTotalVerified(long totalVerified) {
        this.totalVerified = totalVerified;
    }

    public long getMarkedAccurate() {
        return markedAccurate;
    }

    public void setMarkedAccurate(long markedAccurate) {
        this.markedAccurate = markedAccurate;
    }

    public double getAccuracyRate() {
        return accuracyRate;
    }

    public void setAccuracyRate(double accuracyRate) {
        this.accuracyRate = accuracyRate;
    }
}