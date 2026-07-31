package com.company.onboardingadapter.api.dto;

public record OnboardingApplicationResponse(String applicationId, ApplicationStatus status) {

    public enum ApplicationStatus {
        PENDING, APPROVED, REJECTED
    }
}
