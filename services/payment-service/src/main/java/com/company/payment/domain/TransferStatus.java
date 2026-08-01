package com.company.payment.domain;

/** Exactly the guide's own §10.3 async-operation shape, named for payments: a transfer is PENDING until it SETTLED or FAILED. */
public enum TransferStatus {
    PENDING,
    SETTLED,
    FAILED
}
