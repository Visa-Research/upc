package com.visa.vr.upc.sdk.events.callback;

import com.visa.vr.upc.sdk.events.UPCEventHandler;
import com.visa.vr.upc.sdk.generated.UPC2;
import io.reactivex.subscribers.DisposableSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factoy that creates {@link DisposableSubscriber}'s for deposit events.
 */
public class DefaultDepositSubscriberFactory implements IDisposableSubscriberFactory<UPC2.DepositEventResponse> {

    private final UPCEventHandler eventHandler;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public DefaultDepositSubscriberFactory(UPCEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    private class DepositEventSubscriber extends DisposableSubscriber<UPC2.DepositEventResponse> {
        @Override
        public void onNext(UPC2.DepositEventResponse depositEventResponse) {
            logger.info("Subscriber hit deposit event");
            eventHandler.handleDeposit(depositEventResponse);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.info("Deposit error");

            throwable.printStackTrace();
        }

        @Override
        public void onComplete() {
            logger.info("Deposit event complete");
        }
    }

    @Override
    public DisposableSubscriber<UPC2.DepositEventResponse> getSubscriber() {
        return new DepositEventSubscriber();
    }
}
