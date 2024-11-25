package com.visa.vr.upc.sdk;

import org.web3j.abi.datatypes.Address;

/**
 * Some utility functions for working with strings that contain Eth addresses.
 */
public class AddressUtils {

    /**
     * Compares two strings that contain ethereum addresses.
     * @param a
     * @param b
     * @return
     */
    public static boolean isEqual(String a, String b){
        return new Address(a).equals(new Address(b));
    }
}
