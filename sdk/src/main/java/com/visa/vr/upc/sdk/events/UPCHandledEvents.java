package com.visa.vr.upc.sdk.events;

import com.visa.vr.upc.sdk.events.IUPCHandledEvents;

import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of
 */
public class UPCHandledEvents implements IUPCHandledEvents {

    private ConcurrentHashMap<Long, Boolean> closingChannels;

    private ConcurrentHashMap<String, Boolean> handledWithdrawals;

    private ConcurrentHashMap<String, Boolean> handledWithdrawRequests;

    private ConcurrentHashMap<String, Boolean> handledDeposits;

    private ConcurrentHashMap<String, Boolean> handledSetClosings;

    private ConcurrentHashMap<String, Boolean> handledDeployedPromises;

    private ConcurrentHashMap<String, Boolean> handledCloses;


    public UPCHandledEvents() {
        this.closingChannels = new ConcurrentHashMap<>();
        this.handledDeposits = new ConcurrentHashMap<>();
        this.handledCloses = new ConcurrentHashMap<>();
        this.handledDeployedPromises =  new ConcurrentHashMap<>();
        this.handledSetClosings = new ConcurrentHashMap<>();
        this.handledWithdrawals = new ConcurrentHashMap<>();
        this.handledWithdrawRequests = new ConcurrentHashMap<>();
    }

    public Boolean isChannelClosing(long channelId){
        return this.closingChannels.putIfAbsent(channelId, true) != null;
    }

    public Boolean isDepositHandled(String hash){
        return this.handledDeposits.putIfAbsent(hash, true) != null;
    }

    public Boolean isCloseHandled(String hash){
        return this.handledCloses.putIfAbsent(hash, true) != null;
    }

    public Boolean isSetClosingHandled(String hash){
        return this.handledSetClosings.putIfAbsent(hash, true) != null;
    }

    public Boolean isDeployPromiseHandled(String hash){
        return this.handledDeployedPromises.putIfAbsent(hash, true) != null;
    }

    @Override
    public Boolean isWithdrawRequestHandled(String hash) {
        return this.handledWithdrawRequests.putIfAbsent(hash, true) != null;
    }

    public Boolean isWithdrawalHandled(String hash){
        return this.handledWithdrawals.putIfAbsent(hash, true) != null;
    }

}
