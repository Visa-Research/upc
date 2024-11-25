package com.visa.vr.upc.sdk.domain;

import com.visa.vr.upc.sdk.generated.UPC2;
import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.util.concurrent.CompletableFuture;

/**
 * Wraps a UPC smart contract wrapper ({@link UPC2} to expose the address that loaded the contract. This class may disappear in the future.
 */
public class DecoratedUPC {

    private final UPC2 upc;

    private final String fromAddress;

    /**
     * Basic constructor
     * @param upc the UPC smart contract wrapper
     * @param fromAddress the address which loaded (or deployed) the wrapper
     */
    public DecoratedUPC(UPC2 upc, String fromAddress){
        this.upc = upc;
        this.fromAddress = fromAddress;
    }

    /**
     * Loads a UPC wrapper {@link UPC2}, and saves the loading address.
     * @param contractAddress the address of the UPC contract
     * @param web3j
     * @param transactionManager
     * @param contractGasProvider
     */
    public DecoratedUPC(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        this.upc = UPC2.load(contractAddress, web3j, transactionManager, contractGasProvider);
        this.fromAddress = transactionManager.getFromAddress();
    }

    public UPC2 getUpc() {
        return upc;
    }

    public String getFromAddress() {
        return fromAddress;
    }
}
