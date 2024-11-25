package com.visa.vr.upc.sdk.domain;

/**
 * An extension of {@link Promise} that adds some stateful information, including an ID.
 */
public class StatefulPromise extends Promise{

    /**
     * This id should be unique, but it is up to the application to maintain this.
     */
    private Long promiseId;

    /**
     * An integer representing the type of promise. The SDK does not currently define any promise types, so the application may define these freely.
     */
    private Integer promiseType;

    /**
     * A boolean defining whether this promise expiring should trigger the party to close the channel.
     * In general, this should be true for incoming promises and false for outgoing promises,
     * but this is not always the case.
     */
    private Boolean triggerClose;

    /**
     * The status of the promise.
     */
    private PromiseStatus status;

    public StatefulPromise(Promise promise, Long promiseId, Integer promiseType, Boolean triggerClose) {
        super(promise);
        this.promiseId = promiseId;
        this.promiseType = promiseType;
        this.triggerClose = triggerClose;
        this.status = PromiseStatus.OPEN;
    }

    public Long getPromiseId() {
        return promiseId;
    }

    public void setPromiseId(Long promiseId) {
        this.promiseId = promiseId;
    }

    public Integer getPromiseType() {
        return promiseType;
    }

    public void setPromiseType(Integer promiseType) {
        this.promiseType = promiseType;
    }

    public Boolean getTriggerClose() {
        return triggerClose;
    }

    public void setTriggerClose(Boolean triggerClose) {
        this.triggerClose = triggerClose;
    }

    public PromiseStatus getStatus() {
        return status;
    }

    public void setStatus(PromiseStatus status) {
        this.status = status;
    }
}
