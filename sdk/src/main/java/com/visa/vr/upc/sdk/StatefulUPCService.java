package com.visa.vr.upc.sdk;

import com.visa.vr.upc.sdk.custody.ISigner;
import com.visa.vr.upc.sdk.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SignatureException;
import java.util.List;
import java.util.Set;

/**
 * A stateful UPC service. This is not required to use the {@link PromiseService}, but it can help keep
 * channels, promises, and receipt in sync. There should be one StatefulUPCService per party.
 *
 * This class requires interfaces to manage basic CRUD operations of channels, promises, and receipts.
 */
public class StatefulUPCService{

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final IPromiseDataService promiseDataService;

    private final IReceiptDataService receiptDataService;

    private final IChannelDataService channelDataService;

    private final ISigner self;

    /**
     * Basic constructor.
     * @param self
     * @param promiseDataService
     * @param receiptDataService
     * @param channelDataService
     */
    public StatefulUPCService(ISigner self,
                              IPromiseDataService promiseDataService,
                              IReceiptDataService receiptDataService,
                              IChannelDataService channelDataService){
        this.self = self;
        this.promiseDataService = promiseDataService;
        this.receiptDataService = receiptDataService;
        this.channelDataService = channelDataService;
    }

    /**
     * Gets the credit held in a channel by a party
     * @param channelId the channel ID
     * @param address the address of the party
     * @return
     */
    public long getCredit(long channelId, String address) {
        Channel channel = channelDataService.getChannelById(channelId).orElseThrow(IllegalArgumentException::new);
        return channel.getCredit(address);
    }

    /**
     * Gets the amount of available funds held in a channel by the owner of this service. This consists of a party's
     * deposit and credit, minus the other party's credit and the amount of funds currently locked in pending promises.
     * @param channelId
     * @return
     */
    public long getSelfAvailableAmount(long channelId){
        Channel channel = channelDataService.getChannelById(channelId).orElseThrow(IllegalArgumentException::new);
        String other = channel.getOtherAddress(self.getAddress());
        return channel.getDeposit(self.getAddress())
                + channel.getCredit(self.getAddress())
                - channel.getCredit(other)
                - promiseDataService.getOutgoingPendingAmount(channelId);
    }

    /**
     * Gets the amount of available funds held in a channel by the counterparty of the owner of this service.
     * This consists of a party's deposit and credit, minus the other party's credit and the amount of funds
     * currently locked in pending promises.
     * @param channelId
     * @return
     */
    public long getOtherAvailableAmount(long channelId){
        Channel channel = channelDataService.getChannelById(channelId).orElseThrow(IllegalArgumentException::new);
        String other = channel.getOtherAddress(self.getAddress());
        return channel.getDeposit(other)
                + channel.getCredit(other)
                - channel.getCredit(self.getAddress())
                - promiseDataService.getIncomingPendingAmount(channelId);
    }

    /**
     * Creates a receipt based on the current state of outgoing promises.
     * @param channelId
     * @return
     */
    public Receipt createReceipt(long channelId) {
        Channel channel = channelDataService.getChannelById(channelId).orElseThrow(IllegalArgumentException::new);
        if(!channel.checkAddress(self.getAddress())){
            throw new IllegalArgumentException("Invalid address for channel" + channelId);
        }
        String receiver = channel.getOtherAddress(self.getAddress());
        long receiptId = receiptDataService.getLatestOutgoingReceipt(channelId).map(r -> r.getReceiptId().longValue()).orElse(0L) + 1;
        long cumulativeCredit = channel.getTotalCredit(receiver);
        List<? extends Promise> promises = promiseDataService.getOpenOutgoingPromises(channel.getId());
        Receipt receipt = PromiseService.createReceipt(channelId, channel.getChainId(), self.getAddress(), receiver, receiptId, cumulativeCredit, promises);
        receipt = PromiseService.signReceipt(receipt, self);
        receiptDataService.addOutgoingReceipt(receipt);
        return receipt;
    }

    /**
     * Creates a receipt based on the state of outgoing promises after tentatively closing some. Use this
     * to create a receipt that closes promises, without actually closing the promises until after
     * the receipt has been accepted by the counterparty.
     * @param channelId the channel ID
     * @param creditChange the change in credit (due to the closed promises)
     * @param toRemove the promises to remove (the promises that this receipt will close)
     * @return
     */
    public Receipt createReceipt(long channelId, long creditChange, Set<Long> toRemove){
        Channel channel = channelDataService.getChannelById(channelId).orElseThrow(IllegalArgumentException::new);
        if(!channel.checkAddress(self.getAddress())){
            throw new IllegalArgumentException("Invalid address for channel" + channelId);
        }
        String receiver = channel.getOtherAddress(self.getAddress());
        long receiptId = receiptDataService.getLatestOutgoingReceipt(channelId).map(r -> r.getReceiptId().longValue()).orElse(0L) + 1;
        long cumulativeCredit = creditChange + channel.getTotalCredit(receiver);
        List<? extends Promise> promises = promiseDataService.getOutgoingOpenPromisesWithout(channelId, toRemove);
        Receipt receipt = PromiseService.createReceipt(channelId, channel.getChainId(), self.getAddress(), receiver, receiptId, cumulativeCredit, promises);
        receipt = PromiseService.signReceipt(receipt, self);
        return receipt;
    }

    /**
     * Builds an accumulator of open incoming promises.
     * @param channelId
     * @return
     */
    public MerkleAccumulator getIncomingAccumulator(long channelId){
        return PromiseService.getAccumulator(promiseDataService.getOpenIncomingPromises(channelId));
    }

    /**
     * Checks the validity of a receipt against the set of incoming promises.
     * @param receipt
     * @param channelId
     * @param receiptId
     * @param creditChange
     * @param toRemove
     * @return
     * @throws SignatureException
     */
    public Boolean checkReceipt(Receipt receipt, long channelId, long receiptId, long creditChange, Set<Long> toRemove) throws SignatureException {
        Channel channel = channelDataService.getChannelById(channelId).orElseThrow(IllegalArgumentException::new);
        if(!channel.checkAddress(self.getAddress())){
            throw new IllegalArgumentException("Invalid address for channel" + channelId);
        }
        String sender = channel.getOtherAddress(self.getAddress());
        if(receiptId <= receiptDataService.getLatestIncomingReceipt(channelId).map(r -> r.getReceiptId()).orElse(-1L)){
            log.info("Receipt has too low of an index");
            return false;
        }
        long cumulativeCredit = creditChange + channel.getTotalCredit(self.getAddress());
        List<? extends Promise> promises = promiseDataService.getIncomingOpenPromisesWithout(channelId, toRemove);
        if(!PromiseService.verifyReceipt(receipt, channelId, channel.getChainId(), sender, self.getAddress(), receiptId, cumulativeCredit, promises)){
            log.info("Receipt did not verify");
            return false;
        }
        return true;
    }

    /**
     * Creates a promise and adds it to the {@link IPromiseDataService}.
     * @param channelId
     * @param type
     * @param bytecode
     * @param params
     * @param salt
     * @return
     */
    public StatefulPromise createPromise(long channelId,
                                         int type,
                                         String bytecode,
                                         IPromiseConstructorParams params,
                                         byte[] salt){
        Channel channel = channelDataService.getChannelById(channelId).orElseThrow(IllegalArgumentException::new);
        if(!channel.checkAddress(self.getAddress())){
            throw new IllegalArgumentException("Invalid address for channel" + channelId);
        }
        String receiver = channel.getOtherAddress(self.getAddress());
        long receiptId = receiptDataService.getLatestOutgoingReceipt(channelId).map(r -> r.getReceiptId()).orElse(0L);
        Promise promise = PromiseService.createPromise(channelId, channel.getChainId(), channel.getAddress(), self.getAddress(), receiver, receiptId, bytecode, params, salt);
        promise = PromiseService.signPromise(promise, self);
        StatefulPromise statefulPromise = promiseDataService.addOutgoingPromise(promise, type, false);
        return statefulPromise;
    }

    /**
     * Checks the validity of a promise against the current balances held in the channels.
     * @param promise
     * @param channelId
     * @param bytecode
     * @param params
     * @param salt
     * @return
     * @throws SignatureException
     */
    public Boolean checkPromise(Promise promise,
                                long channelId,
                                String bytecode,
                                IPromiseConstructorParams params,
                                byte[] salt) throws SignatureException {
        Channel channel = channelDataService.getChannelById(channelId).orElseThrow(IllegalArgumentException::new);
        if(!channel.checkAddress(self.getAddress())){
            throw new IllegalArgumentException("Invalid address for channel" + channelId);
        }
        String sender = channel.getOtherAddress(self.getAddress());
        long receiptId = receiptDataService.getLatestIncomingReceipt(channelId).map(r -> r.getReceiptId()).orElse(0L);
        if(!PromiseService.verifyPromise(promise, channelId, channel.getChainId(), channel.getAddress(), sender, self.getAddress(), receiptId, bytecode, params, salt)){
            log.info("Promise did not verify");
            return false;
        }
        if(getOtherAvailableAmount(channelId) < promise.getAmount()){
            log.info("Insufficient amount in channel to cover promise");
            return false;
        }
        return true;
    }
}
