package com.visa.vr.upc.sdk.events;

import com.visa.vr.upc.sdk.events.callback.*;
import com.visa.vr.upc.sdk.generated.UPC2;
import io.reactivex.Flowable;
import io.reactivex.subscribers.DisposableSubscriber;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.IOException;
import java.util.List;

/**
 * Provides functionality for the SDK consumer to monitor UPC channels for events.
 */
public class ChannelWatcher {

    private Web3j web3;

    private List<String> watchedChannels;

    private DisposableSubscriber<UPC2.DepositEventResponse> depositSubscriber;

    private DisposableSubscriber<UPC2.CloseEventResponse> closeSubscriber;

    private DisposableSubscriber<UPC2.DeployPromiseEventResponse> deployPromiseSubscriber;

    private DisposableSubscriber<UPC2.SetClosingEventResponse> setClosingSubscriber;

    private DisposableSubscriber<UPC2.WithdrawRequestEventResponse> withdrawRequestSubscriber;

    private DisposableSubscriber<UPC2.WithdrawEventResponse> withdrawSubscriber;

    private IDisposableSubscriberFactory<UPC2.DepositEventResponse> depositFactory;

    private IDisposableSubscriberFactory<UPC2.CloseEventResponse> closeFactory;

    private IDisposableSubscriberFactory<UPC2.DeployPromiseEventResponse> deployPromiseFactory;

    private IDisposableSubscriberFactory<UPC2.SetClosingEventResponse> setClosingFactory;

    private IDisposableSubscriberFactory<UPC2.WithdrawRequestEventResponse> withdrawRequestFactory;

    private IDisposableSubscriberFactory<UPC2.WithdrawEventResponse> withdrawFactory;

    /**
     * Basic constructor.
     * @param web3
     * @param depositFactory
     * @param closeFactory
     * @param deployPromiseFactory
     * @param setClosingFactory
     * @param withdrawRequestFactory
     * @param withdrawFactory
     */
    public ChannelWatcher(Web3j web3,
                          IDisposableSubscriberFactory<UPC2.DepositEventResponse> depositFactory,
                          IDisposableSubscriberFactory<UPC2.CloseEventResponse> closeFactory,
                          IDisposableSubscriberFactory<UPC2.DeployPromiseEventResponse> deployPromiseFactory,
                          IDisposableSubscriberFactory<UPC2.SetClosingEventResponse> setClosingFactory,
                          IDisposableSubscriberFactory<UPC2.WithdrawRequestEventResponse> withdrawRequestFactory,
                          IDisposableSubscriberFactory<UPC2.WithdrawEventResponse> withdrawFactory) {
        this.web3 = web3;
        this.depositFactory = depositFactory;
        this.closeFactory = closeFactory;
        this.deployPromiseFactory = deployPromiseFactory;
        this.setClosingFactory = setClosingFactory;
        this.withdrawRequestFactory = withdrawRequestFactory;
        this.withdrawFactory = withdrawFactory;
    }

    /**
     * Constructor that uses default subscriber factories.
     * @param web3
     * @param eventHandler
     */
    public ChannelWatcher(Web3j web3, UPCEventHandler eventHandler){
        this(web3,
                new DefaultDepositSubscriberFactory(eventHandler),
                new DefaultCloseSubscriberFactory(eventHandler),
                new DefaultDeployPromiseSubscriberFactory(eventHandler),
                new DefaultSetClosingSubscriberFactory(eventHandler),
                new DefaultRequestWithdrawSubscriberFactory(eventHandler),
                new DefaultWithdrawSubscriberFactory(eventHandler)
        );
    }

    private static UPC2 getReadOnlyUPC(Web3j web3){
        return UPC2.load(Address.DEFAULT.toString(), web3, new ReadonlyTransactionManager(web3, Address.DEFAULT.toString()), new DefaultGasProvider());
    }

    /**
     * Get a flowable for deposit events for a list of channels.
     * @param web3
     * @param start
     * @param end
     * @param channels
     * @return
     */
    public static Flowable<UPC2.DepositEventResponse> getDepositEvents(Web3j web3, DefaultBlockParameter start, DefaultBlockParameter end, List<String> channels){
        EthFilter filter = new EthFilter(start, end, channels);
        filter.addSingleTopic(EventEncoder.encode(UPC2.DEPOSIT_EVENT));
        return getReadOnlyUPC(web3).depositEventFlowable(filter);
    }

    /**
     * Get a flowable for close events for a list of channels.
     * @param web3
     * @param start
     * @param end
     * @param channels
     * @return
     */
    public static Flowable<UPC2.CloseEventResponse> getCloseEvents(Web3j web3, DefaultBlockParameter start, DefaultBlockParameter end, List<String> channels){
        EthFilter filter = new EthFilter(start, end, channels);
        filter.addSingleTopic(EventEncoder.encode(UPC2.CLOSE_EVENT));
        return getReadOnlyUPC(web3).closeEventFlowable(filter);
    }

    /**
     * Get a flowable for deploy promise events for a list of channels.
     * @param web3
     * @param start
     * @param end
     * @param channels
     * @return
     */
    public static Flowable<UPC2.DeployPromiseEventResponse> getDeployPromiseEvents(Web3j web3, DefaultBlockParameter start, DefaultBlockParameter end, List<String> channels){
        EthFilter filter = new EthFilter(start, end, channels);
        filter.addSingleTopic(EventEncoder.encode(UPC2.DEPLOYPROMISE_EVENT));
        return getReadOnlyUPC(web3).deployPromiseEventFlowable(filter);
    }

    /**
     * Get a flowable for set closing events for a list of channels.
     * @param web3
     * @param start
     * @param end
     * @param channels
     * @return
     */
    public static Flowable<UPC2.SetClosingEventResponse> getSetClosingEvents(Web3j web3, DefaultBlockParameter start, DefaultBlockParameter end, List<String> channels){
        EthFilter filter = new EthFilter(start, end, channels);
        filter.addSingleTopic(EventEncoder.encode(UPC2.SETCLOSING_EVENT));
        return getReadOnlyUPC(web3).setClosingEventFlowable(filter);
    }

    /**
     * Get a flowable for withdraw request events for a list of channels.
     * @param web3
     * @param start
     * @param end
     * @param channels
     * @return
     */
    public static Flowable<UPC2.WithdrawRequestEventResponse> getWithdrawRequestEvents(Web3j web3, DefaultBlockParameter start, DefaultBlockParameter end, List<String> channels){
        EthFilter filter = new EthFilter(start, end, channels);
        filter.addSingleTopic(EventEncoder.encode(UPC2.WITHDRAWREQUEST_EVENT));
        return getReadOnlyUPC(web3).withdrawRequestEventFlowable(filter);
    }

    /**
     * Get a flowable for withdraw events for a list of channels.
     * @param web3
     * @param start
     * @param end
     * @param channels
     * @return
     */
    public static Flowable<UPC2.WithdrawEventResponse> getWithdrawEvents(Web3j web3, DefaultBlockParameter start, DefaultBlockParameter end, List<String> channels){
        EthFilter filter = new EthFilter(start, end, channels);
        filter.addSingleTopic(EventEncoder.encode(UPC2.WITHDRAW_EVENT));
        return getReadOnlyUPC(web3).withdrawEventFlowable(filter);
    }

    /**
     * Sets the initial list of channels to watch.
     * @param addresses
     * @throws IOException
     */
    public void setChannels(List<String> addresses) throws IOException {
        watchedChannels = addresses;
        setSubscriptions();
    }

    /**
     * Adds a channel to the list of watched channels.
     * @param address
     * @throws IOException
     */
    public void watchChannel(String address) throws IOException {
        watchedChannels.add(address);
        refreshSubscriptions();
    }

    private void setSubscriptions() throws IOException {
        DefaultBlockParameter now = new DefaultBlockParameterNumber(web3.ethBlockNumber().send().getBlockNumber());
        setClosingSubscriber = ChannelWatcher.getSetClosingEvents(web3, now, DefaultBlockParameterName.LATEST, watchedChannels)
                .subscribeWith(setClosingFactory.getSubscriber());
        closeSubscriber = ChannelWatcher.getCloseEvents(web3, now, DefaultBlockParameterName.LATEST, watchedChannels)
                .subscribeWith(closeFactory.getSubscriber());
        deployPromiseSubscriber = ChannelWatcher.getDeployPromiseEvents(web3, now, DefaultBlockParameterName.LATEST, watchedChannels)
                .subscribeWith(deployPromiseFactory.getSubscriber());
        withdrawRequestSubscriber = ChannelWatcher.getWithdrawRequestEvents(web3, now, DefaultBlockParameterName.LATEST, watchedChannels)
                .subscribeWith(withdrawRequestFactory.getSubscriber());
        withdrawSubscriber = ChannelWatcher.getWithdrawEvents(web3, now, DefaultBlockParameterName.LATEST, watchedChannels)
                .subscribeWith(withdrawFactory.getSubscriber());
        depositSubscriber = ChannelWatcher.getDepositEvents(web3, now, DefaultBlockParameterName.LATEST, watchedChannels)
                .subscribeWith(depositFactory.getSubscriber());
    }

    private void disposeSubscriptions(){
        depositSubscriber.dispose();
        setClosingSubscriber.dispose();
        closeSubscriber.dispose();
        withdrawRequestSubscriber.dispose();
        withdrawSubscriber.dispose();
        deployPromiseSubscriber.dispose();
    }

    private void refreshSubscriptions() throws IOException {
        disposeSubscriptions();
        setSubscriptions();
    }
}
