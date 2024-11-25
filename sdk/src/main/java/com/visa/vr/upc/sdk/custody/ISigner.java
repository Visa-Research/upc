package com.visa.vr.upc.sdk.custody;

import org.web3j.crypto.Sign;
import org.web3j.service.TxHSMSignService;
import org.web3j.service.TxSignService;

/**
 * An extension of the TxSignService interface that allows for the signing of raw messages.
 * Should be generic enough to support many types of key custody.
 */
public interface ISigner extends TxSignService {
    Sign.SignatureData signPrefixedMessage(byte[] message);

}
