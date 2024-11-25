package com.visa.vr.upc.sdk.events.callback;

import com.visa.vr.upc.sdk.events.UPCEventHandler;
import com.visa.vr.upc.sdk.generated.UPC2;
import io.reactivex.subscribers.DisposableSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factoy that creates {@link DisposableSubscriber}'s for set closing events.
 */
public class DefaultSetClosingSubscriberFactory implements IDisposableSubscriberFactory<UPC2.SetClosingEventResponse> {

    private final UPCEventHandler eventHandler;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public DefaultSetClosingSubscriberFactory(UPCEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }


    private class SetClosingEventSubscriber extends DisposableSubscriber<UPC2.SetClosingEventResponse> {
        @Override
        public void onNext(UPC2.SetClosingEventResponse setClosingEventResponse) {
            logger.info("Subscriber hit close event");
            eventHandler.handleSetClosing(setClosingEventResponse);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.info("Set closing error");

            throwable.printStackTrace();

        }

        @Override
        public void onComplete() {
            logger.info("Set closing event complete");

        }
    }

    @Override
    public DisposableSubscriber<UPC2.SetClosingEventResponse> getSubscriber() {
        return new SetClosingEventSubscriber();
    }
}
