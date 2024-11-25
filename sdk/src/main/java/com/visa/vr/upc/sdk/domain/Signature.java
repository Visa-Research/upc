package com.visa.vr.upc.sdk.domain;

import com.visa.vr.upc.sdk.generated.UPC2;
import org.web3j.crypto.Sign;
import java.math.BigInteger;


import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a signature
 */
public class Signature implements Serializable {
    private byte[] r;
    private byte[] s;
    private Integer v;

    public byte[] getR() {
        return r;
    }

    public void setR(byte[] r) {
        this.r = r;
    }

    public byte[] getS() {
        return s;
    }

    public void setS(byte[] s) {
        this.s = s;
    }

    public Integer getV() {
        return v;
    }

    public void setV(Integer v) {
        this.v = v;
    }

    public Signature(Sign.SignatureData sig){
        r = sig.getR();
        s = sig.getS();
        v = (int)sig.getV()[0];
    }

    /**
     * Converts to a web3j {@link org.web3j.crypto.Sign.SignatureData} for verification.
     * @return
     */
    public Sign.SignatureData toSignatureData() {
        return new Sign.SignatureData(v.byteValue(), r, s);
    }

    /**
     * Converts to a {@link com.visa.vr.upc.sdk.generated.UPC2.Signature} for use by the smart contract.
     * @return
     */
    public UPC2.Signature toContractSignature(){
        return new UPC2.Signature(BigInteger.valueOf(v.intValue()), r, s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Signature signature = (Signature) o;
        return Arrays.equals(r, signature.r) &&
                Arrays.equals(s, signature.s) &&
                v.equals(signature.v);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(v);
        result = 31 * result + Arrays.hashCode(r);
        result = 31 * result + Arrays.hashCode(s);
        return result;
    }
}
