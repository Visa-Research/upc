package com.visa.vr.upc.sdk.events.callback;

import com.visa.vr.upc.sdk.events.UPCEventHandler;
import com.visa.vr.upc.sdk.generated.UPC2;
import io.reactivex.subscribers.DisposableSubscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factoy that creates {@link DisposableSubscriber}'s for deploy promise events.
 */
public class DefaultDeployPromiseSubscriberFactory implements IDisposableSubscriberFactory<UPC2.DeployPromiseEventResponse> {

    private final UPCEventHandler eventHandler;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public DefaultDeployPromiseSubscriberFactory(UPCEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    private class DeployPromiseEventSubscriber extends DisposableSubscriber<UPC2.DeployPromiseEventResponse> {
        @Override
        public void onNext(UPC2.DeployPromiseEventResponse deployPromiseEventResponse) {
            logger.info("Subscriber hit deploy promise event event");
            eventHandler.handleDeployPromise(deployPromiseEventResponse);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.info("Deploy promise error");
            throwable.printStackTrace();
            logger.info("ERROR: {} ::: ", throwable, throwable.getCause());

        }

        @Override
        public void onComplete() {
            logger.info("Deploy Promise event complete");

        }
    }

    @Override
    public DisposableSubscriber<UPC2.DeployPromiseEventResponse> getSubscriber() {
        return new DeployPromiseEventSubscriber();
    }
}

