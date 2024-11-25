package com.visa.vr.upc.sdk;

import com.visa.vr.upc.sdk.domain.Promise;
import com.visa.vr.upc.sdk.domain.StatefulPromise;

import javax.swing.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An interface that handles promise CRUD operations.
 */
public interface IPromiseDataService{

    StatefulPromise addOutgoingPromise(Promise promise, Integer promiseType, Boolean triggerClose);

    StatefulPromise addIncomingPromise(Promise promise, Long promiseId, Integer promiseType, Boolean triggerClose);

    void updateIncomingPromise(StatefulPromise promise);

    void updateOutgoingPromise(StatefulPromise promise);

    Optional<StatefulPromise> getIncomingPromiseById(Long id);

    Optional<StatefulPromise> getOutgoingPromiseById(Long id);

    Optional<StatefulPromise> getIncomingPromiseByAddress(String address);

    Optional<StatefulPromise> getOutgoingPromiseByAddress(String address);

    List<StatefulPromise> getPromisesByChannel(Long channelId);

    List<StatefulPromise> getOpenPromisesByChannel(Long channelId);

    List<StatefulPromise> getOpenOutgoingPromises(Long channelId);

    List<StatefulPromise> getOpenIncomingPromises(Long channelId);

    long getIncomingPendingAmount(long channelId);

    long getOutgoingPendingAmount(long channelId);

    List<StatefulPromise> getIncomingOpenPromisesWithout(long channelId, Set<Long> toRemove);

    List<StatefulPromise> getOutgoingOpenPromisesWithout(long channelId, Set<Long> toRemove);

    List<StatefulPromise> getExpiringPromises(long channelId);

    Boolean getPromiseExpiring(long channelId);

    void closeIncomingPromises(List<StatefulPromise> promises);

    void closeOutgoingPromises(List<StatefulPromise> promises);
}
