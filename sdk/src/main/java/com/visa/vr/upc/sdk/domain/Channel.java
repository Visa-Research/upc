package com.visa.vr.upc.sdk.domain;

import com.visa.vr.upc.sdk.AddressUtils;
import com.visa.vr.upc.sdk.generated.UPC2;
import org.web3j.abi.datatypes.Address;

import java.math.BigInteger;

/**
 * Represents a single UPC channel between a client and the hub. May become immutable in the future.
 */
public class Channel {

    private Long id;

    private Long chainId;

    private Long claimDuration;

    private String tokenAddress;

    private ChannelStatus status;

    private String address;

    private String clientAddress;

    private String hubAddress;

    private Long clientCredit;

    private Long hubCredit;

    private Long prevClientCredit;

    private Long prevHubCredit;

    private Long clientDeposit;

    private Long hubDeposit;

    /**
     * Main constructor
     * @param hubAddress
     * @param clientAddress
     */
    public Channel(String hubAddress, String clientAddress, long chainId, long claimDuration, String tokenAddress){
        this.hubAddress = hubAddress;
        this.clientAddress = clientAddress;
        this.address = null;
        this.id = null;
        this.chainId = chainId;
        this.claimDuration = claimDuration;
        this.tokenAddress = tokenAddress;
        this.status = ChannelStatus.STARTED;
        this.clientCredit = 0L;
        this.prevClientCredit = 0L;
        this.hubCredit = 0L;
        this.prevHubCredit = 0L;
        this.clientDeposit = 0L;
        this.hubDeposit = 0L;
    }

    /**
     * Copy Constructor
     * @param channel
     */
    public Channel(Channel channel){
        this.hubAddress = channel.hubAddress;
        this.clientAddress = channel.clientAddress;
        this.address = channel.address;
        this.id = channel.id;
        this.chainId = channel.chainId;
        this.claimDuration = channel.claimDuration;
        this.tokenAddress = channel.tokenAddress;
        this.status = channel.status;
        this.clientCredit = channel.clientCredit;
        this.hubCredit = channel.hubCredit;
        this.prevClientCredit = channel.prevClientCredit;
        this.prevHubCredit = channel.prevHubCredit;
        this.clientDeposit = channel.clientDeposit;
        this.hubDeposit = channel.hubDeposit;
    }

    /**
     * Converts into {@link com.visa.vr.upc.sdk.generated.UPC2.ChannelParams} as used by the UPC smart contract to create a new channel.
     * @return
     */
    public UPC2.ChannelParams toContractParams(){
        return new UPC2.ChannelParams(BigInteger.valueOf(this.id),
                BigInteger.valueOf(this.chainId),
                this.hubAddress,
                this.clientAddress,
                BigInteger.valueOf(this.claimDuration),
                this.tokenAddress);
    }

    /**
     * Verifies that the address belongs to one of the two parties in the channel.
     * @param address
     * @return
     */
    public boolean checkAddress(String address){
        return AddressUtils.isEqual(clientAddress, address) || AddressUtils.isEqual(hubAddress, address);
    }

    /**
     * Given an address of one party of the channel, returns the other party
     * @param address
     * @return
     */
    public String getOtherAddress(String address){
        if(AddressUtils.isEqual(clientAddress, address)){
            return hubAddress;
        }
        if(AddressUtils.isEqual(hubAddress, address)){
            return clientAddress;
        }
        throw new IllegalArgumentException("Address not member of channel");
    }

    /**
     * Gets the credit amount associated with the given party
     * @param address
     * @return
     */
    public long getCredit(String address){
        if(AddressUtils.isEqual(clientAddress, address)){
            return clientCredit;
        }
        if(AddressUtils.isEqual(hubAddress, address)){
            return hubCredit;
        }
        throw new IllegalArgumentException("Address not member of channel");
    }

    /**
     * Gets the deposit amount associated with the given party
     * @param address
     * @return
     */
    public long getDeposit(String address){
        if(AddressUtils.isEqual(clientAddress, address)){
            return clientDeposit;
        }
        if(AddressUtils.isEqual(hubAddress, address)){
            return hubDeposit;
        }
        throw new IllegalArgumentException("Address not member of channel");
    }

    /**
     * Sets the credit amount associated with the given party
     * @param address
     * @param credit
     */
    public void setCredit(String address, long credit){
        if(AddressUtils.isEqual(clientAddress, address)){
            clientCredit = credit;
            return;
        }
        if(AddressUtils.isEqual(hubAddress, address)){
            hubCredit = credit;
            return;
        }
        throw new IllegalArgumentException("Address not member of channel");
    }

    /**
     * Sets the deposit amount associated with the given party
     * @param address
     * @param deposit
     */
    public void setDeposit(String address, long deposit){
        if(AddressUtils.isEqual(clientAddress, address)){
            clientDeposit = deposit;
            return;
        }
        if(AddressUtils.isEqual(hubAddress, address)){
            hubDeposit = deposit;
            return;
        }
        throw new IllegalArgumentException("Address not member of channel");
    }

    /**
     * Adds to the credit amount associated with the given party. Can be negative.
     * @param address
     * @param toAdd
     */
    public void addCredit(String address, long toAdd){
        if(AddressUtils.isEqual(clientAddress, address)){
            clientCredit += toAdd;
            return;
        }
        if(AddressUtils.isEqual(hubAddress, address)){
            hubCredit += toAdd;
            return;
        }
        throw new IllegalArgumentException("Address not member of channel");
    }

    /**
     * Adds to the deposit amount associated with the given party. Can be negative.
     * @param address
     * @param toAdd
     */
    public void addDeposit(String address, long toAdd){
        if(AddressUtils.isEqual(clientAddress, address)){
            clientDeposit += toAdd;
            return;
        }
        if(AddressUtils.isEqual(hubAddress, address)){
            hubDeposit += toAdd;
            return;
        }
        throw new IllegalArgumentException("Address not member of channel");
    }

    public long getTotalCredit(String address){
        if(AddressUtils.isEqual(clientAddress, address)){
            return clientCredit + prevClientCredit;
        }
        if(AddressUtils.isEqual(hubAddress, address)){
            return hubCredit + prevHubCredit;
        }
        throw new IllegalArgumentException("Address not member of channel");
    }

    public void rolloverCredit(){
        prevClientCredit += clientCredit;
        prevHubCredit += hubCredit;
        clientCredit = 0L;
        hubCredit = 0L;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChainId() {
        return chainId;
    }

    public void setChainId(Long chainId) {
        this.chainId = chainId;
    }

    public Long getClaimDuration() {
        return claimDuration;
    }

    public void setClaimDuration(Long claimDuration) {
        this.claimDuration = claimDuration;
    }

    public String getTokenAddress() {
        return tokenAddress;
    }

    public void setTokenAddress(String tokenAddress) {
        this.tokenAddress = tokenAddress;
    }

    public ChannelStatus getStatus() {
        return status;
    }

    public void setStatus(ChannelStatus status) {
        this.status = status;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public String getHubAddress() {
        return hubAddress;
    }

    public void setHubAddress(String hubAddress) {
        this.hubAddress = hubAddress;
    }

    public Long getClientCredit() {
        return clientCredit;
    }

    public void setClientCredit(long clientCredit) {
        this.clientCredit = clientCredit;
    }

    public Long getHubCredit() {
        return hubCredit;
    }

    public void setHubCredit(long hubCredit) {
        this.hubCredit = hubCredit;
    }

    public Long getPrevClientCredit() {
        return prevClientCredit;
    }

    public void setPrevClientCredit(Long prevClientCredit) {
        this.prevClientCredit = prevClientCredit;
    }

    public Long getPrevHubCredit() {
        return prevHubCredit;
    }

    public void setPrevHubCredit(Long prevHubCredit) {
        this.prevHubCredit = prevHubCredit;
    }

    public Long getClientDeposit() {
        return clientDeposit;
    }

    public void setClientDeposit(long clientDeposit) {
        this.clientDeposit = clientDeposit;
    }

    public Long getHubDeposit() {
        return hubDeposit;
    }

    public void setHubDeposit(long hubDeposit) {
        this.hubDeposit = hubDeposit;
    }
}
