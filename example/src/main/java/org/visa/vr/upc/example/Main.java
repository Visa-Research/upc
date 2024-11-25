package org.visa.vr.upc.example;

import com.visa.vr.upc.sdk.*;
import com.visa.vr.upc.sdk.custody.BasicSigner;
import com.visa.vr.upc.sdk.custody.ISigner;
import com.visa.vr.upc.sdk.domain.*;
import com.visa.vr.upc.sdk.events.ChannelWatcher;
import com.visa.vr.upc.sdk.events.DefaultUPCEventHandler;
import com.visa.vr.upc.sdk.events.UPCEventHandler;
import com.visa.vr.upc.sdk.events.UPCHandledEvents;
import com.visa.vr.upc.sdk.events.callback.*;
import com.visa.vr.upc.sdk.generated.ERC20PresetFixedSupply;
import com.visa.vr.upc.sdk.generated.HTLC;
import com.visa.vr.upc.sdk.generated.UPC2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.impl.SimpleLogger;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.*;

public class Main {

    private static final String node_url = "http://127.0.0.1:8545";
    private static final String coinbase_sk = "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
    private static final String hub_sk = "FE4EAEFBE4076F4D726ABD8B20335543E4210524AD3DF189C2BBF3A756702FFE";
    private static final String client_sk = "36A110380859D05E60C2991479DE4D24DA3116A49B342127C1F1F7D536CAE547";
    private static final Random random = new Random();
    private static Logger logger;
    public static ContractGasProvider getFreeGasProvider(Web3j web3) throws IOException {
        BigInteger gasLimit = web3.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getGasLimit();
        return new StaticGasProvider(BigInteger.ZERO, gasLimit);
    }

    public static void logChannelView(StatefulUPCService hubService, StatefulUPCService clientService, long channelId) {
        logger.info("Hub View | Hub Available: {}, Client Available:  {}", hubService.getSelfAvailableAmount(channelId), hubService.getOtherAvailableAmount(channelId));
        logger.info("Client View | Hub Available: {}, Client Available: {}", clientService.getOtherAvailableAmount(channelId), clientService.getSelfAvailableAmount(channelId));

    }

    public static byte[] getSalt() {
        byte[] salt = new byte[32];
        random.nextBytes(salt);
        return salt;
    }

    public static void main(String[] args) throws Exception {

        logger = LoggerFactory.getLogger(Main.class);

        // 0) Setup
        // This setup is not related to the UPC SDK, but initiates the web3 connection, token, and hub and client.

        // Setup web3j
        Web3j web3 = Web3j.build(new HttpService(node_url));
        Long chainId = web3.ethChainId().send().getChainId().longValue();
        ContractGasProvider gasProvider = getFreeGasProvider(web3);
        Credentials masterCreds = Credentials.create(coinbase_sk);

        // Setup hub and client credentials, signers, and transaction managers
        Credentials hubCreds = Credentials.create(hub_sk);
        ISigner hub = new BasicSigner(hubCreds.getEcKeyPair());
        TransactionManager hubTransactionManager = new FastRawTransactionManager(web3, hubCreds);


        Credentials clientCreds = Credentials.create(client_sk);
        ISigner client = new BasicSigner(clientCreds.getEcKeyPair());
        TransactionManager clientTransactionManager = new FastRawTransactionManager(web3, clientCreds);

        // Create the ERC20 token, and give 500 to both the hub and the client
        ERC20PresetFixedSupply hubToken = ERC20PresetFixedSupply.deploy(web3, hubTransactionManager, gasProvider, "Token2", "T2", BigInteger.valueOf(1000), hub.getAddress()).send();
        hubToken.transfer(client.getAddress(), BigInteger.valueOf(500)).send();
        ERC20PresetFixedSupply clientToken = ERC20PresetFixedSupply.load(hubToken.getContractAddress(), web3, clientTransactionManager, gasProvider);


        // 1) Create the UPC related services for both the hub and client

        logger.info("1) INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        UPCEventHandler hubEventHandler = new DefaultUPCEventHandler(new UPCHandledEvents(), hubDataService, hubDataService, hubDataService);
        ChannelWatcher hubChannelWatcher = new ChannelWatcher(web3, hubEventHandler);

        UPCEventHandler clientEventHandler = new DefaultUPCEventHandler(new UPCHandledEvents(), clientDataService, clientDataService, clientDataService);
        ChannelWatcher clientChannelWatcher = new ChannelWatcher(web3, clientEventHandler);

        // 2) Create and deploy a channel
        logger.info("2) CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), chainId, 120, hubToken.getContractAddress());
        Channel hubChannel = hubDataService.createChannel(initChannel);

        UPC2.ChannelParams channelParams = hubChannel.toContractParams();
        logger.info("Deploying channel");
        DecoratedUPC hubUPC = UPCService.createChannel(web3, hubTransactionManager, gasProvider, channelParams).join();
        hubChannel.setAddress(hubUPC.getUpc().getContractAddress());
        logger.info("Deployed channel");

        hubDataService.updateChannel(hubChannel);

        Channel clientChannel = new Channel(hubChannel);
        DecoratedUPC clientUPC = UPCService.loadUPCContract(web3, clientChannel.getAddress(), clientTransactionManager, gasProvider);
        clientDataService.addChannel(clientChannel);

        // Tell both the hub and client to watch this channel (for events)
        hubChannelWatcher.setChannels(Collections.singletonList(hubChannel.getAddress()));
        clientChannelWatcher.setChannels(Collections.singletonList(clientChannel.getAddress()));

        logChannelView(hubUPCService, clientUPCService, hubChannel.getId());

        // 3) Deposit into the channel

        logger.info("3) DEPOSIT INTO CHANNEL");

        logger.info("Depositing into channel");
        CompletableFuture<TransactionReceipt> clientDepositFuture = clientToken.approve(clientChannel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if (t != null) {
                        logger.info("Approve failed: {}", t.getMessage());
                        throw new CompletionException(t);
                    }
                    logger.info("Approve succeeded.");
                    return UPCService.deposit(clientUPC, 100).join();
                }
        );


        CompletableFuture<TransactionReceipt> hubDepositFuture = hubToken.approve(hubChannel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if (t != null) {
                        logger.info("Approve failed: {}", t.getMessage());
                        throw new CompletionException(t);
                    }
                    logger.info("Approve succeeded.");
                    return UPCService.deposit(hubUPC, 100).join();
                }
        );

        CompletableFuture.allOf(clientDepositFuture, hubDepositFuture).join();

        UPCState state = UPCState.fromContract(hubUPC.getUpc().getState().send());

        logger.info("Deposits - Hub: {}, Client: {}", state.getHub().deposit, state.getClient().deposit);

        logChannelView(hubUPCService, clientUPCService, hubChannel.getId());

        // 4) Client creates an HTLC promise to the hub
        logger.info("4) CLIENT CREATES AN HTLC PROMISE");

        byte[] secret1 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt1 = getSalt();
        HTLCConstructorParams params1 = new HTLCConstructorParams(10, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc1 = clientUPCService.createPromise(
                clientChannel.getId(),
                1,
                HTLC.BINARY,
                params1,
                salt1);

        // 5) Hub verifies the promise and adds it
        logger.info("5) HUB VERIFIES THE PROMISE");
        assert hubUPCService.checkPromise(htlc1, hubChannel.getId(), HTLC.BINARY, params1, salt1);
        hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);
        logChannelView(hubUPCService, clientUPCService, hubChannel.getId());

        // 6) Client sends a receipt
        logger.info("6) CLIENT SENDS A RECEIPT");
        Receipt receipt1 = clientUPCService.createReceipt(clientChannel.getId(), 10, Collections.singleton(htlc1.getPromiseId()));

        logger.info("7) HUB VERIFIES THE RECEIPT");
        // Hub verifies the receipt and adds it
        assert hubUPCService.checkReceipt(receipt1, hubChannel.getId(), receipt1.getReceiptId(), 10, Collections.singleton(htlc1.getPromiseId()));
        hubDataService.closeIncomingPromises(Collections.singletonList(htlc1));
        hubDataService.addIncomingReceipt(receipt1, receipt1.getReceiptId());
        hubChannel.setHubCredit(receipt1.getCumulativeCredit());
        hubDataService.updateChannel(hubChannel);

        clientDataService.closeOutgoingPromises(Collections.singletonList(htlc1));
        clientDataService.addOutgoingReceipt(receipt1);
        clientChannel.setHubCredit(receipt1.getCumulativeCredit());
        clientDataService.updateChannel(clientChannel);

        logChannelView(hubUPCService, clientUPCService, hubChannel.getId());

        // 8) Client creates a second HTLC promise to the hub
        logger.info("8) CLIENT CREATES A SECOND PROMISE");
        byte[] secret2 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt2 = getSalt();
        HTLCConstructorParams params2 = new HTLCConstructorParams(10, Hash.sha256(secret2), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc2 = clientUPCService.createPromise(
                clientChannel.getId(),
                1,
                HTLC.BINARY,
                params2,
                salt2);

        // 9) Hub verifies the promise and adds it
        logger.info("9) HUB VERIFIES THE PROMISE");
        assert hubUPCService.checkPromise(htlc2, hubChannel.getId(), HTLC.BINARY, params2, salt2);
        hubDataService.addIncomingPromise(htlc2, htlc2.getPromiseId(), htlc2.getPromiseType(), true);
        logChannelView(hubUPCService, clientUPCService, hubChannel.getId());

        // 10) Client never sends a receipt, so the hub deploys the promise
        logger.info("10) CLIENT DOES NOT SEND RECEIPT, SO HUB GOES ON-CHAIN");
        hubEventHandler.closeChannel(hubChannel.getId());
        Receipt latestReceipt = hubDataService.getLatestIncomingReceipt(hubChannel.getId()).get();
        UPCService.deployReceipt(hubUPC, latestReceipt).join();
        UPCService.deployPromise(hubUPC, htlc2, Optional.of(receipt1), null).join();
        HTLC htlc2Promise = HTLC.load(htlc2.getAddress(), web3, hubTransactionManager, gasProvider);
        htlc2Promise.revealKey(secret2).send();
        UPCService.close(hubUPC).join();

        state = UPCState.fromContract(hubUPC.getUpc().getState().send());
        long secondsUntilWithdraw = BigInteger.valueOf(state.getExpiry()).add(BigInteger.valueOf(30)).subtract(BigInteger.valueOf(Instant.now().getEpochSecond())).longValue();
        logger.info("Seconds until withdraw: {}", secondsUntilWithdraw);

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.schedule(() -> {
            logger.info("Channel should be closed");
            UPCService.withdraw(hubUPC).join();
            logChannelView(hubUPCService, clientUPCService, hubChannel.getId());
        }, secondsUntilWithdraw, TimeUnit.SECONDS).get();

        BigInteger hubBalance = hubToken.balanceOf(hub.getAddress()).send();
        BigInteger clientBalance = hubToken.balanceOf(client.getAddress()).send();
        logger.info("Token balance of Hub: {}", hubBalance);
        logger.info("Token balance of Client: {}", clientBalance);

    }
}
