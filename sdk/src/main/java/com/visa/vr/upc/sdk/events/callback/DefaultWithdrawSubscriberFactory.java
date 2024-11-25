package com.visa.vr.upc.sdk.events.callback;

import com.visa.vr.upc.sdk.events.UPCEventHandler;
import com.visa.vr.upc.sdk.generated.UPC2;
import io.reactivex.subscribers.DisposableSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factoy that creates {@link DisposableSubscriber}'s for withdraw events.
 */
public class DefaultWithdrawSubscriberFactory implements IDisposableSubscriberFactory<UPC2.WithdrawEventResponse> {

    private final UPCEventHandler eventHandler;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public DefaultWithdrawSubscriberFactory(UPCEventHandler eventHandler) {

        this.eventHandler = eventHandler;
    }

    private class WithdrawEventSubscriber extends DisposableSubscriber<UPC2.WithdrawEventResponse> {
        @Override
        public void onNext(UPC2.WithdrawEventResponse withdrawEventResponse) {
            logger.info("Subscriber hit withdraw event");
            eventHandler.handleWithdraw(withdrawEventResponse);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.info("Withdraw error");

            throwable.printStackTrace();

        }

        @Override
        public void onComplete() {
            logger.info("Withdraw event complete");

        }
    }

    @Override
    public DisposableSubscriber<UPC2.WithdrawEventResponse> getSubscriber() {
        return new WithdrawEventSubscriber();
    }
}
