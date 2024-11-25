package com.visa.vr.upc.sdk.events;

/**
 * An interface to keep track of which events have already been handled. The implementation should be thread-safe.
 */
public interface IUPCHandledEvents {
    public Boolean isChannelClosing(long channelId);

    public Boolean isDepositHandled(String hash);

    public Boolean isCloseHandled(String hash);

    public Boolean isSetClosingHandled(String hash);

    public Boolean isDeployPromiseHandled(String hash);

    public Boolean isWithdrawRequestHandled(String hash);

    public Boolean isWithdrawalHandled(String hash);
}
