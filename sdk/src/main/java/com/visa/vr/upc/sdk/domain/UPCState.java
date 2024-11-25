package com.visa.vr.upc.sdk.domain;

import com.visa.vr.upc.sdk.generated.UPC2;
import org.web3j.tuples.generated.Tuple4;

import java.math.BigInteger;

/**
 * Represents the state of a UPC channel. This class exists to provide an easier-to-read version of the smart contract struct.
 */
public class UPCState {

    private final ChannelStatus channelStatus;

    private final long expiry;

    private final UPC2.Party hub;

    private final UPC2.Party client;

    private UPCState(ChannelStatus channelStatus, UPC2.Party hub, UPC2.Party client, long expiry){
        this.channelStatus = channelStatus;
        this.expiry = expiry;
        this.hub = hub;
        this.client = client;
    }

    /**
     * Converts from the state returned by the UPC smart contract wrapper {@link UPC2}.
     * @param contractState
     * @return
     */
    public static UPCState fromContract(Tuple4<BigInteger, UPC2.Party, UPC2.Party, BigInteger> contractState) {
        return new UPCState(ChannelStatus.fromUPCContract(contractState.component1().intValue()),
                contractState.component2(),
                contractState.component3(),
                contractState.component4().longValue());

    }

    public ChannelStatus getChannelStatus() {
        return channelStatus;
    }

    public long getExpiry() {
        return expiry;
    }

    public UPC2.Party getHub() {
        return hub;
    }

    public UPC2.Party getClient() {
        return client;
    }
}
