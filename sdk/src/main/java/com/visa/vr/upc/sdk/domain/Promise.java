package com.visa.vr.upc.sdk.domain;

import com.visa.vr.upc.sdk.generated.UPC2;
import org.web3j.utils.Numeric;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * Represents a promise
 */
public class Promise implements Serializable {

    private Long channelId;

    private Long chainId;

    private String sender;

    private String receiver;

    private String address;

    private String bytecode;

    private Long receiptId;

    private Long amount;

    private byte[] salt;

    private Signature signature;

    private Long expiration;

    /**
     * An empty constructor.
     */
    public Promise(){}

    /**
     * A copy constructor.
     * @param promise
     */
    public Promise(Promise promise) {
        this.channelId = promise.channelId;
        this.chainId = promise.chainId;
        this.sender = promise.sender;
        this.receiver = promise.receiver;
        this.address = promise.address;
        this.bytecode = promise.bytecode;
        this.receiptId = promise.receiptId;
        this.amount = promise.amount;
        this.salt = promise.salt;
        this.signature = promise.signature;
        this.expiration = promise.expiration;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public Long getChainId() {
        return chainId;
    }

    public void setChainId(Long chainId) {
        this.chainId = chainId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBytecode() {
        return bytecode;
    }

    public void setBytecode(String bytecode) {
        this.bytecode = bytecode;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public byte[] getSalt() {
        return salt;
    }

    public void setSalt(byte[] salt) {
        this.salt = salt;
    }

    public Signature getSignature() {
        return signature;
    }

    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    public Long getExpiration() {
        return expiration;
    }

    public void setExpiration(Long expiration) {
        this.expiration = expiration;
    }

    /**
     * Converts the promise to a {@link com.visa.vr.upc.sdk.generated.UPC2.Promise} to be used in the smart contract.
     * @return
     */
    public UPC2.Promise toContractPromise(){
        return new UPC2.Promise(sender, receiver, BigInteger.valueOf(receiptId), new BigInteger(1, salt), Numeric.hexStringToByteArray(bytecode));
    }

    @Override
    public String toString() {
        return "Promise{" +
                "channelId=" + channelId +
                ", sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                ", address='" + address + '\'' +
                ", credit=" + receiptId +
                ", amount=" + amount +
                ", expiration=" + expiration +
                '}';
    }
}
