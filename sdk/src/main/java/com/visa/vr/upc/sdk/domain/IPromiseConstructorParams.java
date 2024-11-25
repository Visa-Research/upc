package com.visa.vr.upc.sdk.domain;

/**
 * An interface for the constructor params of a UPC promise.
 */
public interface IPromiseConstructorParams {

    /**
     * Returns the ABI-encoded constructor params, as they will appear in the bytecode of the deployed promise contract.
     * @return
     */
    String encodePacked();

    Long getAmount();

    Long getExpiration();
}
