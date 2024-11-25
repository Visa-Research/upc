package com.visa.vr.upc.sdk.events;

import com.visa.vr.upc.sdk.IChannelDataService;
import com.visa.vr.upc.sdk.IPromiseDataService;
import com.visa.vr.upc.sdk.IReceiptDataService;
import com.visa.vr.upc.sdk.domain.Channel;
import com.visa.vr.upc.sdk.domain.ChannelStatus;
import com.visa.vr.upc.sdk.domain.PromiseStatus;
import com.visa.vr.upc.sdk.domain.StatefulPromise;
import com.visa.vr.upc.sdk.generated.UPC2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Default implementation of the {@link UPCEventHandler}. This class will is not intended to be used
 * by the consuming application, but only provides an example of what kind of features it should have.
 */
public class DefaultUPCEventHandler extends UPCEventHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final IChannelDataService channelDataService;

    private final IPromiseDataService promiseDataService;

    private final IReceiptDataService receiptDataService;

    public DefaultUPCEventHandler(IUPCHandledEvents upcHandledEvents,
                                  IChannelDataService channelDataService,
                                  IPromiseDataService promiseDataService,
                                  IReceiptDataService receiptDataService) {
        super(upcHandledEvents);
        this.channelDataService = channelDataService;
        this.promiseDataService = promiseDataService;
        this.receiptDataService = receiptDataService;
    }

    @Override
    public void internalHandleDeposit(UPC2.DepositEventResponse event) {
        logger.info("Recording a deposit");
        String address = event.log.getAddress();
        Channel channel = channelDataService.getChannelById(event.id.longValue()).orElseThrow(RuntimeException::new);
        channel.addDeposit(event.from, event.amount.longValue());
        channelDataService.updateChannel(channel);
    }

    @Override
    public void internalHandleSetClosing(UPC2.SetClosingEventResponse event) {
        logger.info("Recording a set closing");
        String address = event.log.getAddress();
        closeChannel(event.id.longValue());
    }

    @Override
    public void internalHandleClose(UPC2.CloseEventResponse event) {
        logger.info("Recording a close");
        Channel channel = channelDataService.getChannelById(event.id.longValue()).orElseThrow(RuntimeException::new);
        channel.setStatus(ChannelStatus.CLOSED);
        channelDataService.updateChannel(channel);
    }

    @Override
    public void internalHandleDeployPromise(UPC2.DeployPromiseEventResponse event) {
        logger.info("Recording a deploy promise");
        Optional<StatefulPromise> incomingPromise = promiseDataService.getIncomingPromiseByAddress(event.promiseAddress);
        Optional<StatefulPromise> outgoingPromise = promiseDataService.getOutgoingPromiseByAddress(event.promiseAddress);
        if(incomingPromise.isPresent() && outgoingPromise.isPresent()){
            logger.info("Warning: both incoming and outgoing promise have the same address.");
        }
        StatefulPromise promise;
        if(incomingPromise.isPresent()){
            promise = incomingPromise.get();
            promise.setStatus(PromiseStatus.DEPLOYED);
            promiseDataService.updateIncomingPromise(promise);
            return;
        }
        else if(outgoingPromise.isPresent()){
            promise = outgoingPromise.get();
            promise.setStatus(PromiseStatus.DEPLOYED);
            promiseDataService.updateOutgoingPromise(promise);
            return;
        }
        logger.info("Could not find promise with deployed address");
    }

    @Override
    public void internalHandleWithdrawRequest(UPC2.WithdrawRequestEventResponse event){
        logger.info("Recording a withdraw request");
    }

    @Override
    public void internalHandleWithdraw(UPC2.WithdrawEventResponse event) {
        logger.info("Recording a withdrawal");
        if(event.fullWithdrawal){
            Channel channel = channelDataService.getChannelById(event.id.longValue()).orElseThrow(RuntimeException::new);
            channel.setStatus(ChannelStatus.WITHDRAWN);
            channelDataService.updateChannel(channel);
        }
        else{
            Channel channel = channelDataService.getChannelById(event.id.longValue()).orElseThrow(RuntimeException::new);
            channel.setClientDeposit(event.clientDeposit.longValue());
            channel.setHubDeposit(event.hubDeposit.longValue());
            channel.setPrevClientCredit(event.clientPrevCredit.longValue());
            channel.setPrevHubCredit(event.hubPrevCredit.longValue());
            channel.setClientCredit(0L);
            channel.setHubCredit(0L);
            channelDataService.updateChannel(channel);
        }
    }

    @Override
    public void internalCloseChannel(long channelId) {
        Channel channel = channelDataService.getChannelById(channelId).orElseThrow(RuntimeException::new);
        channel.setStatus(ChannelStatus.CLOSING);
        channelDataService.updateChannel(channel);
    }
}
