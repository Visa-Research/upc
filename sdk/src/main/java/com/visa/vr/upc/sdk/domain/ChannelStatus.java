package com.visa.vr.upc.sdk.domain;

/**
 * An enum to represent the status of a channel. Roughly matches ChannelStatus in the smart contract.
 */
public enum ChannelStatus {
    STARTED(0),
    DEPLOYED(1),
    CLOSING(2),
    CLOSED(3),
    WITHDRAWN(4),
    SERVER_ERROR(5);

    private int value;

    private ChannelStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public Boolean isError() {
        return value > 4;
    }

    /**
     * Converts from a smart contract status to this enum.
     * @param value
     * @return
     */
    static public ChannelStatus fromUPCContract(int value){
        switch(value){
            case 0:
                return DEPLOYED;
            case 1:
                return CLOSING;
            case 2:
                return CLOSED;
            case 3:
                return WITHDRAWN;
        }
        return SERVER_ERROR;
    }
}