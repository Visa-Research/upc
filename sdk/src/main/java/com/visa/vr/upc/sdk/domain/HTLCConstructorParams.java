package com.visa.vr.upc.sdk.domain;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Type;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * The constructor params for the HTLC promise.
 */
public class HTLCConstructorParams implements IPromiseConstructorParams {

    private Long amount;
    private byte[] hash;
    private Long expiration;

    @Override
    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public byte[] getHash() {
        return hash;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    @Override
    public Long getExpiration() {
        return expiration;
    }

    public void setExpiration(Long expiration) {
        this.expiration = expiration;
    }

    public HTLCConstructorParams(long amount, byte[] hash, long expiration) {
        this.amount = amount;
        this.hash = hash;
        this.expiration = expiration;
    }

    @Override
    public String encodePacked() {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(amount),
                new org.web3j.abi.datatypes.generated.Bytes32(hash),
                new org.web3j.abi.datatypes.generated.Uint256(expiration)));
        return encodedConstructor;
    }
}
