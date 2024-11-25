package com.visa.vr.upc.sdk.events.callback;

import com.visa.vr.upc.sdk.events.UPCEventHandler;
import com.visa.vr.upc.sdk.generated.UPC2;
import io.reactivex.subscribers.DisposableSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factoy that creates {@link DisposableSubscriber}'s for withdraw request events.
 */
public class DefaultRequestWithdrawSubscriberFactory implements IDisposableSubscriberFactory<UPC2.WithdrawRequestEventResponse> {

    private final UPCEventHandler eventHandler;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public DefaultRequestWithdrawSubscriberFactory(UPCEventHandler eventHandler) {

        this.eventHandler = eventHandler;
    }

    private class WithdrawRequestEventSubscriber extends DisposableSubscriber<UPC2.WithdrawRequestEventResponse> {
        @Override
        public void onNext(UPC2.WithdrawRequestEventResponse withdrawRequestEventResponse) {
            logger.info("Subscriber hit withdraw request event");
            eventHandler.handleWithdrawRequest(withdrawRequestEventResponse);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.info("Withdraw request error");

            throwable.printStackTrace();

        }

        @Override
        public void onComplete() {
            logger.info("Withdraw request event complete");

        }
    }

    @Override
    public DisposableSubscriber<UPC2.WithdrawRequestEventResponse> getSubscriber() {
        return new WithdrawRequestEventSubscriber();
    }
}
