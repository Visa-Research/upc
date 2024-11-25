package com.visa.vr.upc.sdk;


import com.visa.vr.upc.sdk.domain.Promise;
import com.visa.vr.upc.sdk.domain.Receipt;
import com.visa.vr.upc.sdk.domain.DecoratedUPC;
import com.visa.vr.upc.sdk.domain.UPCState;
import com.visa.vr.upc.sdk.generated.UPC2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


/**
 * Provides methods for loading and interacting with the UPC smart contract.
 */
public class UPCService {

    private static final Logger log = LoggerFactory.getLogger(UPCService.class);

    /**
     * Loads a UPC contract from the given address. Note that this method does not check that the given address actually
     * contains the expected bytecode. Unless you are sure that the address is safe, use {@link #loadUPCContract(Web3j, String, TransactionManager, ContractGasProvider)}.
     * @param web3j
     * @param address
     * @param transactionManager
     * @param gasProvider
     * @return A UPC contract wrapper with the loading address, see {@link DecoratedUPC}
     */
    public static DecoratedUPC unsafeLoadUPCContract(Web3j web3j, String address, TransactionManager transactionManager, ContractGasProvider gasProvider) {
        return new DecoratedUPC(address, web3j, transactionManager, gasProvider);
    }

    /**
     * Loads a UPC contract from a given address.
     * @param web3j
     * @param address
     * @param transactionManager
     * @param gasProvider
     * @return A UPC contract wrapper with the loading address, see {@link DecoratedUPC}
     * @throws IOException if the address does not contain the expected bytecode
     */
    public static DecoratedUPC loadUPCContract(Web3j web3j, String address, TransactionManager transactionManager, ContractGasProvider gasProvider) throws IOException {
        DecoratedUPC decoratedUPC = UPCService.unsafeLoadUPCContract(web3j, address, transactionManager, gasProvider);
        if(!decoratedUPC.getUpc().isValid()){
            throw new IllegalArgumentException("UPC Contract not found at given address");
        }
        return decoratedUPC;
    }

    /**
     * Asynchronously deploys the UPC smart contract.
     * @param web3j
     * @param transactionManager
     * @param gasProvider
     * @param channelParams
     * @return the {@link CompletableFuture} of the {@link DecoratedUPC} that points to the smart contract.
     */
    public static CompletableFuture<DecoratedUPC> createChannel(Web3j web3j, TransactionManager transactionManager, ContractGasProvider gasProvider, UPC2.ChannelParams channelParams){
        return UPC2.deploy(web3j, transactionManager, gasProvider, channelParams).sendAsync().whenComplete((UPC2 upc, Throwable t) -> {
            if(t != null){
                log.info("Failed to deploy UPC Contract with Channel ID {}, exception: {}", channelParams.cid.longValue(), t.getMessage());
                return;
            }
        }).thenApply(u -> new DecoratedUPC(u, transactionManager.getFromAddress()));
    }

    /**
     * Asynchronously makes a deposit to a UPC smart contract. This uses a transferFrom, so the depositing address
     * should call approve on the ERC20 token contract first.
     * @param decoratedUPC the smart contract wrapper
     * @param amount the amount to deposit
     * @return the {@link CompletableFuture} of the {@link TransactionReceipt}
     */
    public static CompletableFuture<TransactionReceipt> deposit(DecoratedUPC decoratedUPC, long amount){
        UPC2 upc = decoratedUPC.getUpc();
        return upc.depositToken(BigInteger.valueOf(amount)).sendAsync().whenComplete((TransactionReceipt r,  Throwable t) -> {
            if(t != null){
                log.info("Failed to deposit into UPC Contract with address {}, exception: {}", upc.getContractAddress(), t.getMessage());
                return;
            }
            log.info("Deposited to channel with address {}", upc.getContractAddress());
            UPC2.getDepositEvents(r).stream().forEach(e -> {
                log.debug("Deposit from {} of {} to UPC channel at {}", e.from, e.amount, upc.getContractAddress());
            });
        });
    }

    /**
     * Asynchronously deploys a promise to the UPC smart contract. This may require a Merkle proof from the root
     * of the latest receipt.
     * @param decoratedUPC the UPC smart contract wrapper
     * @param promise the promise to deploy
     * @param latestReceipt the latest receipt received by the party deploying the promise. This is not required if
     *                      1) the sender of the promise is deploying it,
     *                      2) the promise was sent after the latest receipt
     *                      3) no receipt has been sent yet
     * @param acc the Merkle accumulator of the latest receipt, pass null if not needed
     * @return the {@link CompletableFuture} of the {@link TransactionReceipt}
     * @throws IOException
     */
    public static CompletableFuture<TransactionReceipt> deployPromise(DecoratedUPC decoratedUPC, Promise promise, Optional<Receipt> latestReceipt, MerkleAccumulator acc) throws Exception {
        byte[] proof;
        if(promise.getSender() == decoratedUPC.getFromAddress()) {
            log.debug("Promise being deployed by sender");
            proof = new byte[32];
        } else if(!latestReceipt.isPresent()) {
            log.debug("Deploying promise without receipt");
            proof = new byte[32];
        } else if(promise.getReceiptId() >= latestReceipt.get().getReceiptId()) {
            log.debug("Promise id is after receipt");
            proof = new byte[32];
        }
        else {
            proof = PromiseService.getAccumulatorProof(acc, promise);
        }
        return deployPromise(decoratedUPC.getUpc(), promise, proof);
    }

    /**
     * A lower level version of {@link #deployPromise(DecoratedUPC, Promise, Optional, MerkleAccumulator)}, which includes the proof directly.
     * @param upc the UPC smart contract wrapper
     * @param promise the promise to deploy
     * @param proof the Merkle proof from the promise to the root of the latest receipt, pass an empty byte[32] if not needed
     * @return the {@link CompletableFuture} of the {@link TransactionReceipt}
     */
    public static CompletableFuture<TransactionReceipt> deployPromise(UPC2 upc, Promise promise, byte[] proof) {
        ArrayList<byte[]> proofList = new ArrayList<>();
        for(int i = 0; i < proof.length; i+= 32){
            proofList.add(Arrays.copyOfRange(proof, i, i + 32));
        }

        return upc.registerPromise(promise.toContractPromise(), promise.getSignature().toContractSignature(), proofList
                ).sendAsync()
                .whenComplete((TransactionReceipt r, Throwable t) -> {
                   if(t != null){
                       log.info("Failed to deploy promise to UPC Contract by {} with address {}, exception: {}", promise.getSender(), upc.getContractAddress(), t.getMessage());
                       return;
                   }
                   log.info("Deployed promise");

                   UPC2.getDeployPromiseEvents(r).stream().forEach((UPC2.DeployPromiseEventResponse e) -> {
                       log.info("Deployed promise {} {} {}", e.from, e.id, e.promiseAddress);
                   });
                });
    }

    /**
     * Asynchronously deploys a receipt to the UPC smart contract
     * @param decoratedUPC
     * @param receipt
     * @return the {@link CompletableFuture} of the {@link TransactionReceipt}
     */
    public static CompletableFuture<TransactionReceipt> deployReceipt(DecoratedUPC decoratedUPC, Receipt receipt) {
        UPC2 upc = decoratedUPC.getUpc();
        return upc.registerReceipt(receipt.toContractReceipt(), receipt.getSignature().toContractSignature())
                .sendAsync().whenComplete((TransactionReceipt r, Throwable t) -> {
                    if(t != null){
                        log.info("Failed to deploy receipt to UPC Contract by {} with address {}, exception: {}", receipt.getReceiver(), upc.getContractAddress(), t.getMessage());
                        return;
                    }
                    log.info("Deployed receipt");

        });
    }

    /**
     * Asynchronously deploys an empty receipt to the UPC smart contract. This is needed in the situation where
     * a receiver wants to deploy a promise, but has not yet been sent a receipt. The receiver can deploy a receipt
     * with credit of 0 without a valid signature from the sender.
     * @param decoratedUPC
     * @return the {@link CompletableFuture} of the {@link TransactionReceipt}
     */
    public static CompletableFuture<TransactionReceipt> deployEmptyReceipt(DecoratedUPC decoratedUPC) {
        UPC2 upc = decoratedUPC.getUpc();
        return upc.registerReceipt(
                        new UPC2.Receipt(BigInteger.ZERO, BigInteger.ZERO, Bytes32.DEFAULT.getValue()),
                        new UPC2.Signature(BigInteger.ZERO, Bytes32.DEFAULT.getValue(), Bytes32.DEFAULT.getValue()))
                .sendAsync().whenComplete((TransactionReceipt r, Throwable t) -> {
                    if (t != null) {
                        log.info("Failed to deploy empty receipt to UPC Contract by {} with address {}, exception: {}", decoratedUPC.getFromAddress(), upc.getContractAddress(), t.getMessage());
                        return;
                    }
                    log.info("Deployed receipt");

                });
    }

    /**
     * Asynchronously closes the channel. This will set the channel to closing and allow both parties to
     * deploy receipts and promises.
     * @param decoratedUPC
     * @return the {@link CompletableFuture} of the {@link TransactionReceipt}
     */
    public static CompletableFuture<TransactionReceipt> close(DecoratedUPC decoratedUPC){
        UPC2 upc = decoratedUPC.getUpc();
        return upc.close().sendAsync().whenComplete((TransactionReceipt r, Throwable t) -> {
            if(t != null){
                log.info("Failed to close the UPC contract address {}, exception: {}", upc.getContractAddress(), t.getMessage());
                return;
            }
            log.info("Closed channel");
            UPC2.getCloseEvents(r).stream().forEach(e -> {
                log.debug("Closed UPC channel {} at {} by {}", e.id, upc.getContractAddress(), e.from);
            });
        });
    }

    /**
     * Asynchronously soft withdraws from the UPC smart contract. This will pause the channel, initiate a period where
     * both parrties can deploy receipts, and then after a timeout, or both parties' preemption, withdraws a given amount and
     * resumes the channel.
     * @param decoratedUPC
     * @param amount
     * @return the {@link CompletableFuture} of the {@link TransactionReceipt}
     */
    public static CompletableFuture<TransactionReceipt> withdrawNotClosing(DecoratedUPC decoratedUPC, long amount){
        UPC2 upc = decoratedUPC.getUpc();
        return upc.withdrawNotClosing(BigInteger.valueOf(amount)).sendAsync().whenComplete((TransactionReceipt r, Throwable t) -> {
            if(t != null){
                log.info("Failed to soft withdraw from UPC contract address {}, exception: {}", upc.getContractAddress(), t.getMessage());
                return;
            }
            log.info("Initiated soft withdrawal");
            UPC2.getWithdrawRequestEvents(r).stream().forEach(e -> {
                log.debug("Started a soft withdrawal for {} from UPC channel {} at {} by {}", e.amount, e.id, upc.getContractAddress(), e.from);
            });
        });
    }

    /**
     * Asynchronously hard withdraws from the UPC smart contract. Requires that the channel is already closed.
     * This will withdraw all tokens to their respective owners and self-destruct the contract.
     * @param decoratedUPC
     * @return the {@link CompletableFuture} of the {@link TransactionReceipt}
     */
    public static CompletableFuture<TransactionReceipt> withdraw(DecoratedUPC decoratedUPC){
        UPC2 upc = decoratedUPC.getUpc();
        return upc.withdrawClosing().sendAsync().whenComplete((TransactionReceipt r, Throwable t) -> {
            if(t != null){
                log.info("Failed to hard withdraw from UPC contract address {}, exception: {}", upc.getContractAddress(), t.getMessage());
                return;
            }
            log.info("Initiated hard withdrawal");
            UPC2.getWithdrawEvents(r).stream().forEach(e -> {
                log.debug("Made a hard withdrawal from UPC channel {} at {} by {}", e.id, upc.getContractAddress(), decoratedUPC.getFromAddress());
            });
        });
    }

    /**
     * Calls {@link #getState(UPC2)}
     * @param decoratedUPC
     * @return
     */
    public static CompletableFuture<UPCState> getState(DecoratedUPC decoratedUPC){
        return getState(decoratedUPC.getUpc());
    }

    /**
     * Asynchronously gets the state of the UPC smart contract
     * @param upc
     * @return
     */
    public static CompletableFuture<UPCState> getState(UPC2 upc){
        return upc.getState().sendAsync().thenApply(s -> UPCState.fromContract(s)).whenComplete((UPCState s, Throwable t) -> {
            if (t != null) {
                log.info("Failed to get the UPC State from contract {}, exception: {}", upc.getContractAddress(), t.getMessage());
                return;
            }
        });
    }
}
