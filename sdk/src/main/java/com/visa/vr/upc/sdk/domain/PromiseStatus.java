package com.visa.vr.upc.sdk.domain;

/**
 * An enum to represent the status of a promise.
 */
public enum PromiseStatus {
    OPEN(0),
    CLOSED(1),
    DEPLOYED(2),
    REJECTED(3),
    SERVER_ERROR(4);

    private int value;

    private PromiseStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public Boolean isError() {
        return value > 3;
    }

}