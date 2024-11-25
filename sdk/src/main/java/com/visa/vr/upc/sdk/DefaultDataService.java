package com.visa.vr.upc.sdk;

import com.visa.vr.upc.sdk.domain.*;
import kotlin.Pair;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A basic implementation of all three DataService interfaces that uses in-memory HashMaps. This is just
 * a demonstrative implementation, and should not be used in practice.
 */
public class DefaultDataService implements IChannelDataService, IPromiseDataService, IReceiptDataService{

    private final HashMap<Long, Channel> channels;

    private final HashMap<Long, StatefulPromise> incomingPromises;

    private final HashMap<Long, StatefulPromise> outgoingPromises;

    private final HashMap<Pair<Long, Long>, Receipt> outgoingReceipts;

    private final HashMap<Pair<Long, Long>, Receipt> incomingReceipts;

    private Long nextChannel = 1L;

    private Long nextPromise = 1L;

    private final long EXPIRATION_WINDOW = 300;

    private final String selfAddress;

    public DefaultDataService(String selfAddress){
        channels = new HashMap<>();
        incomingPromises = new HashMap<>();
        incomingReceipts = new HashMap<>();
        outgoingPromises = new HashMap<>();
        outgoingReceipts = new HashMap<>();
        this.selfAddress = selfAddress;
    }

    @Override
    public Channel createChannel(Channel channel) {
        channel.setId(nextChannel);
        channels.put(nextChannel, channel);
        nextChannel += 1;
        return channel;
    }

    @Override
    public void addChannel(Channel channel){
        //TODO: Check id
        channels.put(channel.getId(), channel);
    }

    @Override
    public void updateChannel(Channel channel) {
        if(!channels.containsKey(channel.getId())){
            throw new IllegalArgumentException("Channel not found");
        }
        channels.put(channel.getId(), channel);
    }

    @Override
    public Optional<Channel> getChannelById(Long id) {
        return Optional.ofNullable(channels.getOrDefault(id, null));
    }

    @Override
    public Optional<Channel> getChannelByAddress(String address) {
        return channels.values().stream()
                .filter(c -> AddressUtils.isEqual(c.getAddress(), address))
                .findFirst();
    }

    @Override
    public List<Channel> getChannelsByStatus(ChannelStatus status) {
        return channels.values().stream().filter((Channel c) -> c.getStatus().equals(status)).collect(Collectors.toList());
    }

    @Override
    public StatefulPromise addOutgoingPromise(Promise promise, Integer promiseType, Boolean triggerClose) {
        StatefulPromise statefulPromise = new StatefulPromise(promise, nextPromise, promiseType, triggerClose);
        outgoingPromises.put(nextPromise, statefulPromise);
        nextPromise += 1;
        return statefulPromise;
    }

    @Override
    public StatefulPromise addIncomingPromise(Promise promise, Long promiseId, Integer promiseType, Boolean triggerClose) {
        StatefulPromise statefulPromise = new StatefulPromise(promise, promiseId, promiseType, triggerClose);
        incomingPromises.put(promiseId, statefulPromise);
        return statefulPromise;
    }

    @Override
    public void updateIncomingPromise(StatefulPromise promise) {
        if(!incomingPromises.containsKey(promise.getPromiseId())){
            throw new IllegalArgumentException("Promise not found");
        }
        incomingPromises.put(promise.getPromiseId(), promise);
    }

    @Override
    public void updateOutgoingPromise(StatefulPromise promise) {
        if(!outgoingPromises.containsKey(promise.getPromiseId())){
            throw new IllegalArgumentException("Promise not found");
        }
        outgoingPromises.put(promise.getPromiseId(), promise);
    }

    @Override
    public Optional<StatefulPromise> getIncomingPromiseById(Long id) {
        return Optional.ofNullable(incomingPromises.getOrDefault(id, null));
    }

    @Override
    public Optional<StatefulPromise> getOutgoingPromiseById(Long id) {
        return Optional.ofNullable(outgoingPromises.getOrDefault(id, null));
    }

    @Override
    public Optional<StatefulPromise> getIncomingPromiseByAddress(String address){
        return incomingPromises.values().stream().filter(
                (StatefulPromise p) -> AddressUtils.isEqual(p.getAddress(), address)).findFirst();
    }

    @Override
    public Optional<StatefulPromise> getOutgoingPromiseByAddress(String address){
        return outgoingPromises.values().stream().filter(
                (StatefulPromise p) -> AddressUtils.isEqual(p.getAddress(), address)).findFirst();
    }


    @Override
    public List<StatefulPromise> getPromisesByChannel(Long channelId) {
        return Stream.concat(incomingPromises.values().stream(),
                outgoingPromises.values().stream()).filter((StatefulPromise p) -> p.getChannelId() == channelId)
                .collect(Collectors.toList());
    }

    @Override
    public List<StatefulPromise> getOpenPromisesByChannel(Long channelId) {
        return Stream.concat(incomingPromises.values().stream(),
                outgoingPromises.values().stream())
                .filter((StatefulPromise p) -> p.getChannelId() == channelId
                        && p.getStatus().equals(PromiseStatus.OPEN))
                .collect(Collectors.toList());
    }

    @Override
    public List<StatefulPromise> getOpenOutgoingPromises(Long channelId){
        return outgoingPromises.values().stream().filter(
                (StatefulPromise p) -> p.getChannelId() == channelId
                        && p.getStatus().equals(PromiseStatus.OPEN))
                .collect(Collectors.toList());
    }

    @Override
    public List<StatefulPromise> getOpenIncomingPromises(Long channelId) {
        return incomingPromises.values().stream().filter(
                (StatefulPromise p) -> p.getChannelId() == channelId
                        && p.getStatus().equals(PromiseStatus.OPEN))
                .collect(Collectors.toList());
    }

    @Override
    public long getIncomingPendingAmount(long channelId) {
        return getOpenIncomingPromises(channelId).stream()
                .mapToLong((StatefulPromise p) -> p.getAmount()).sum();
    }

    @Override
    public long getOutgoingPendingAmount(long channelId) {
        return getOpenOutgoingPromises(channelId).stream()
                .mapToLong((StatefulPromise p) -> p.getAmount()).sum();
    }

    @Override
    public List<StatefulPromise> getIncomingOpenPromisesWithout(long channelId, Set<Long> toRemove){
        return incomingPromises.values().stream().filter(
                (StatefulPromise p) -> p.getChannelId() == channelId
                        && p.getStatus().equals(PromiseStatus.OPEN)
                        && !toRemove.contains(p.getPromiseId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<StatefulPromise> getOutgoingOpenPromisesWithout(long channelId, Set<Long> toRemove){
        return outgoingPromises.values().stream().filter(
                (StatefulPromise p) -> p.getChannelId() == channelId
                        && p.getStatus().equals(PromiseStatus.OPEN)
                        && !toRemove.contains(p.getPromiseId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<StatefulPromise> getExpiringPromises(long channelId) {
        long threshold = Instant.now().plusSeconds(EXPIRATION_WINDOW).getEpochSecond();
        return Stream.concat(incomingPromises.values().stream(),
                outgoingPromises.values().stream())
                .filter((StatefulPromise p) -> p.getChannelId() == channelId
                        && p.getTriggerClose()
                        && p.getExpiration() < threshold)
                .collect(Collectors.toList());
    }

    @Override
    public Boolean getPromiseExpiring(long channelId) {
        return !getExpiringPromises(channelId).isEmpty();
    }

    @Override
    public void closeIncomingPromises(List<StatefulPromise> promises) {
        for (StatefulPromise promise: promises) {
            promise.setStatus(PromiseStatus.CLOSED);
            updateIncomingPromise(promise);
        }
    }

    @Override
    public void closeOutgoingPromises(List<StatefulPromise> promises) {
        for (StatefulPromise promise: promises) {
            promise.setStatus(PromiseStatus.CLOSED);
            updateOutgoingPromise(promise);
        }
    }

    @Override
    public void addOutgoingReceipt(Receipt receipt) {
        if(outgoingReceipts.putIfAbsent(
                new Pair<Long, Long>(receipt.getChannelId(), receipt.getReceiptId()), receipt) != null){
            throw new IllegalArgumentException("Receipt already exists");
        }
    }

    @Override
    public void addIncomingReceipt(Receipt receipt, Long receiptId) {
        if(incomingReceipts.putIfAbsent(
                new Pair<Long, Long>(receipt.getChannelId(), receipt.getReceiptId()), receipt) != null){
            throw new IllegalArgumentException("Receipt already exists");
        }
    }

    @Override
    public Optional<Receipt> getIncomingReceiptById(long channelId, long receiptId) {
        return Optional.ofNullable(incomingReceipts.getOrDefault(new Pair(channelId, receiptId), null));
    }

    @Override
    public Optional<Receipt> getOutgoingReceiptById(long channelId, long receiptId) {
        return Optional.ofNullable(outgoingReceipts.getOrDefault(new Pair(channelId, receiptId), null));
    }

    @Override
    public Optional<Receipt> getLatestIncomingReceipt(long channelId) {
        return incomingReceipts.values().stream()
                .filter((Receipt r) -> r.getChannelId() == channelId)
                .max(Comparator.comparingLong(r -> r.getReceiptId()));
    }

    @Override
    public Optional<Receipt> getLatestOutgoingReceipt(long channelId){
        return outgoingReceipts.values().stream()
                .filter((Receipt r) -> r.getChannelId() == channelId)
                .max(Comparator.comparingLong(r -> r.getReceiptId()));
    }
}
