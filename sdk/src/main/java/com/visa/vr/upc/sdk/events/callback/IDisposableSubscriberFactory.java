package com.visa.vr.upc.sdk.events.callback;

import io.reactivex.subscribers.DisposableSubscriber;

/**
 * An interface for a factory that creates DisposableSubscribers, to be used in event handling.
 * @param <T>
 */
public interface IDisposableSubscriberFactory<T> {

    public DisposableSubscriber<T> getSubscriber();
}
