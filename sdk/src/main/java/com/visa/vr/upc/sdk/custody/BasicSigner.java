package com.visa.vr.upc.sdk.custody;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;
import org.web3j.service.TxSignServiceImpl;

/**
 * An implementation of {@link ISigner} that wraps an {@link ECKeyPair}.
 */
public class BasicSigner extends TxSignServiceImpl implements ISigner {

    private final ECKeyPair keyPair;

    public BasicSigner(ECKeyPair _keyPair) {
        super(Credentials.create(_keyPair));
        keyPair = _keyPair;
    }

    @Override
    public Sign.SignatureData signPrefixedMessage(byte[] message) {
        return Sign.signPrefixedMessage(message, keyPair);
    }
}
