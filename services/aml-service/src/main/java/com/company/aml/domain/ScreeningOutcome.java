package com.company.aml.domain;

/** Only meaningful once {@link ScreeningStatus#COMPLETED} — a compliance determination, not a system state. */
public enum ScreeningOutcome {
    CLEAR,
    HIT
}
