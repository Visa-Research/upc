package com.visa.vr.upc.sdk.events.callback;

import com.visa.vr.upc.sdk.events.UPCEventHandler;
import com.visa.vr.upc.sdk.generated.UPC2;
import io.reactivex.subscribers.DisposableSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factoy that creates {@link DisposableSubscriber}'s for close events.
 */
public class DefaultCloseSubscriberFactory implements IDisposableSubscriberFactory<UPC2.CloseEventResponse> {

    private final UPCEventHandler eventHandler;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public DefaultCloseSubscriberFactory(UPCEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    private class CloseEventSubscriber extends DisposableSubscriber<UPC2.CloseEventResponse> {
        @Override
        public void onNext(UPC2.CloseEventResponse closeEventResponse) {
            logger.info("Subscriber hit close event");
            eventHandler.handleClose(closeEventResponse);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.info("Close error");

            throwable.printStackTrace();

        }

        @Override
        public void onComplete() {
            logger.info("Close event complete");

        }
    }

    @Override
    public DisposableSubscriber<UPC2.CloseEventResponse> getSubscriber() {
        return new CloseEventSubscriber();
    }
}
