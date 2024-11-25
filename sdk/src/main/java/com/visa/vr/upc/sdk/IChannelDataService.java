package com.visa.vr.upc.sdk;

import com.visa.vr.upc.sdk.domain.Channel;
import com.visa.vr.upc.sdk.domain.ChannelStatus;

import java.util.List;
import java.util.Optional;

/**
 * An interface that handles channel CRUD operations.
 */
public interface IChannelDataService {

    public Channel createChannel(Channel channel);

    public void addChannel(Channel channel);

    public void updateChannel(Channel channel);

    public Optional<Channel> getChannelById(Long id);

    public Optional<Channel> getChannelByAddress(String address);

    public List<Channel> getChannelsByStatus(ChannelStatus status);
}
