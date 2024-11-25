package com.visa.vr.upc.sdk.domain;

import com.visa.vr.upc.sdk.generated.UPC2;

import java.math.BigInteger;

/**
 * Represents a receipt
 */
public class Receipt {

    private String sender;

    private String receiver;

    private Long channelId;

    private Long chainId;

    private Long cumulativeCredit;

    private Long receiptId;

    private byte[] accumulatorRoot;

    private Signature signature;

    /**
     * Empty constructor.
     */
    public Receipt(){}

    /**
     * Copy constructor.
     * @param receipt
     */
    public Receipt(Receipt receipt){
        this.channelId = receipt.channelId;
        this.chainId = receipt.chainId;
        this.sender = receipt.sender;
        this.receiver = receipt.receiver;
        this.cumulativeCredit = receipt.cumulativeCredit;
        this.receiptId = receipt.receiptId;
        this.accumulatorRoot = receipt.accumulatorRoot;
        this.signature = receipt.signature;
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

    public Long getCumulativeCredit() {
        return cumulativeCredit;
    }

    public void setCumulativeCredit(Long cumulativeCredit) {
        this.cumulativeCredit = cumulativeCredit;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
    }

    public byte[] getAccumulatorRoot() {
        return accumulatorRoot;
    }

    public void setAccumulatorRoot(byte[] accumulatorRoot) {
        this.accumulatorRoot = accumulatorRoot;
    }

    public Signature getSignature() {
        return signature;
    }

    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    /**
     * Converts to a {@link com.visa.vr.upc.sdk.generated.UPC2.Receipt} for use by the smart contract
     * @return
     */
    public UPC2.Receipt toContractReceipt(){
        return new UPC2.Receipt(BigInteger.valueOf(receiptId), BigInteger.valueOf(cumulativeCredit), accumulatorRoot);
    }
}
