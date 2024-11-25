package com.visa.vr.upc.sdk.events;

import com.visa.vr.upc.sdk.generated.UPC2;

/**
 * Abstract class that provides functions to handle events. Each event should be handled only once.
 */
public abstract class UPCEventHandler {

    private final IUPCHandledEvents upcHandledEvents;

    public UPCEventHandler(IUPCHandledEvents upcHandledEvents){
        this.upcHandledEvents = upcHandledEvents;
    }

    public void handleDeposit(UPC2.DepositEventResponse event){
        if(!upcHandledEvents.isDepositHandled(event.log.getTransactionHash())){
            internalHandleDeposit(event);
        }
    }

    public void handleSetClosing(UPC2.SetClosingEventResponse event){
        if(!upcHandledEvents.isSetClosingHandled(event.log.getTransactionHash())){
            internalHandleSetClosing(event);
        }
    }

    public void handleClose(UPC2.CloseEventResponse event){
        if(!upcHandledEvents.isCloseHandled(event.log.getTransactionHash())){
            internalHandleClose(event);
        }
    }

    public void handleDeployPromise(UPC2.DeployPromiseEventResponse event){
        if(!upcHandledEvents.isDeployPromiseHandled(event.log.getTransactionHash())){
            internalHandleDeployPromise(event);
        }
    }

    public void handleWithdrawRequest(UPC2.WithdrawRequestEventResponse event){
        if(!upcHandledEvents.isWithdrawRequestHandled(event.log.getTransactionHash())){
            internalHandleWithdrawRequest(event);
        }
    }

    public void handleWithdraw(UPC2.WithdrawEventResponse event){
        if(!upcHandledEvents.isWithdrawalHandled(event.log.getTransactionHash())){
            internalHandleWithdraw(event);
        }
    }

    public void closeChannel(long channelId){
        if(!upcHandledEvents.isChannelClosing(channelId)){
            internalCloseChannel(channelId);
        }
    }

    abstract protected void internalHandleDeposit(UPC2.DepositEventResponse event);

    abstract protected void internalHandleSetClosing(UPC2.SetClosingEventResponse event);

    abstract protected void internalHandleClose(UPC2.CloseEventResponse event);

    abstract protected void internalHandleDeployPromise(UPC2.DeployPromiseEventResponse event);

    abstract protected void internalHandleWithdrawRequest(UPC2.WithdrawRequestEventResponse event);

    abstract protected void internalHandleWithdraw(UPC2.WithdrawEventResponse event);

    abstract protected void internalCloseChannel(long channelId);


}
