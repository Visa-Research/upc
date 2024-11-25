package com.visa.vr.upc.sdk.e2e;

import com.visa.vr.upc.sdk.*;
import com.visa.vr.upc.sdk.custody.BasicSigner;
import com.visa.vr.upc.sdk.custody.ISigner;
import com.visa.vr.upc.sdk.domain.*;
import com.visa.vr.upc.sdk.events.ChannelWatcher;
import com.visa.vr.upc.sdk.events.DefaultUPCEventHandler;
import com.visa.vr.upc.sdk.events.UPCEventHandler;
import com.visa.vr.upc.sdk.events.UPCHandledEvents;
import com.visa.vr.upc.sdk.generated.ERC20PresetFixedSupply;
import com.visa.vr.upc.sdk.generated.HTLC;
import com.visa.vr.upc.sdk.generated.UPC2;
import com.visa.vr.upc.sdk.unit.UPCConsistencyTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;


public class HTLCFlows {

    private static Random random;
    private static Logger logger;

    private static Web3j web3;

    private static long chainId;

    private static ContractGasProvider gasProvider;

    private Credentials hubCreds;
    private Credentials clientCreds;
    private ISigner hub;
    private ISigner client;
    private TransactionManager hubTransactionManager;
    private TransactionManager clientTransactionManager;

    private ERC20PresetFixedSupply hubToken;

    private ERC20PresetFixedSupply clientToken;




    public static ContractGasProvider getFreeGasProvider(Web3j web3) throws IOException {
        BigInteger gasLimit = web3.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getGasLimit();
        logger.info("Gas Limit: {}", gasLimit);
        return new StaticGasProvider(BigInteger.ZERO, gasLimit);
    }

    public static void logChannelView(StatefulUPCService hubService, StatefulUPCService clientService, long channelId){
        logger.info("(Hub View) Hub: {}, Client: {}", hubService.getSelfAvailableAmount(channelId), hubService.getOtherAvailableAmount(channelId));
        logger.info("(Client View) Hub: {}, Client: {}", clientService.getOtherAvailableAmount(channelId), clientService.getSelfAvailableAmount(channelId));
    }

    public static byte[] getSalt(){
        byte[] salt = new byte[32];
        random.nextBytes(salt);
        return salt;
    }

    @NotNull
    public static String getNewPrivateKey(){
        return Numeric.toHexString(getSalt());
    }

    @BeforeAll
    public static void setupAll() throws IOException {
        Properties properties = new Properties();
        properties.load(UPCConsistencyTest.class.getResourceAsStream("/test.properties"));
        String nodeUrl = properties.getProperty("nodeUrl");
        HTLCFlows.logger = LoggerFactory.getLogger(HTLCFlows.class);
        HTLCFlows.random = new Random();
        HTLCFlows.web3 = Web3j.build(new HttpService(nodeUrl));
        HTLCFlows.chainId = HTLCFlows.web3.ethChainId().getId();
        HTLCFlows.gasProvider = getFreeGasProvider(web3);
    }

    @BeforeEach
    public void setupEach() throws Exception {
        this.hubCreds = Credentials.create(getNewPrivateKey());
        this.hub = new BasicSigner(hubCreds.getEcKeyPair());
        this.hubTransactionManager = new FastRawTransactionManager(web3, hubCreds);


        this.clientCreds = Credentials.create(getNewPrivateKey());
        this.client = new BasicSigner(clientCreds.getEcKeyPair());
        this.clientTransactionManager = new FastRawTransactionManager(web3, clientCreds);

        hubToken = ERC20PresetFixedSupply.deploy(
                web3, hubTransactionManager, gasProvider,
                "Token2", "T2", BigInteger.valueOf(1000), hub.getAddress()).send();
        hubToken.transfer(client.getAddress(), BigInteger.valueOf(500)).send();
        clientToken = ERC20PresetFixedSupply.load(
                hubToken.getContractAddress(), web3, clientTransactionManager, gasProvider);

        logger.info("Token balance of Hub({}): {}", hub.getAddress(), hubToken.balanceOf(hub.getAddress()).send());
        logger.info("Token balance of Client({}): {}", client.getAddress(), hubToken.balanceOf(client.getAddress()).send());
    }

    @AfterEach
    public void tearDownEach() throws Exception {
           }

    @Test
    public void HTLC_Happy_Flow() throws Exception {

        logger.info("INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        logger.info("CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), HTLCFlows.chainId, 120, hubToken.getContractAddress());
        Channel channel = hubDataService.createChannel(initChannel);

        UPC2.ChannelParams channelParams = channel.toContractParams();
        logger.info("Deploying channel");
        DecoratedUPC hubUPC = UPCService.createChannel(web3, hubTransactionManager, gasProvider, channelParams).join();
        channel.setAddress(hubUPC.getUpc().getContractAddress());
        logger.info("Deployed channel");

        hubDataService.updateChannel(channel);
        DecoratedUPC clientUPC = UPCService.loadUPCContract(web3, channel.getAddress(), clientTransactionManager, gasProvider);
        clientDataService.addChannel(channel);

        logger.info("DEPOSIT INTO CHANNEL");

        logger.info("Depositing into channel");
        CompletableFuture<TransactionReceipt> clientDepositFuture = clientToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
                        logger.info("Approve failed: {}", t.getMessage());
                        throw new CompletionException(t);
                    }
                    logger.info("Approve succeeded.");
                    return UPCService.deposit(clientUPC, 100).join();
                }
        );

        CompletableFuture<TransactionReceipt> hubDepositFuture = hubToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
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
        channel.addDeposit(hub.getAddress(), state.getHub().deposit.longValue());
        channel.addDeposit(client.getAddress(), state.getClient().deposit.longValue());
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        // 4) Client creates an HTLC promise to the hub
        byte[] secret1 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt1 = getSalt();
        HTLCConstructorParams params1 = new HTLCConstructorParams(10, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc1 = clientUPCService.createPromise(
                channel.getId(),
                1,
                HTLC.BINARY,
                params1,
                salt1);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc1, channel.getId(), HTLC.BINARY, params1, salt1)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, channel.getId());



        // 5) Client sends a receipt
        Receipt receipt1 = clientUPCService.createReceipt(channel.getId(), 10, Collections.singleton(htlc1.getPromiseId()));

        // Hub verifies the receipt and adds it
        if(!hubUPCService.checkReceipt(receipt1, channel.getId(), receipt1.getReceiptId(), 10, Collections.singleton(htlc1.getPromiseId()))){
            throw new RuntimeException("Receipt verify failed");
        }
        hubDataService.closeIncomingPromises(Collections.singletonList(htlc1));
        hubDataService.addIncomingReceipt(receipt1, receipt1.getReceiptId());
        channel.setCredit(hub.getAddress(), receipt1.getCumulativeCredit());
        hubDataService.updateChannel(channel);

        clientDataService.closeOutgoingPromises(Collections.singletonList(htlc1));
        clientDataService.addOutgoingReceipt(receipt1);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());


        UPCService.deployReceipt(hubUPC, receipt1).join();
        UPCService.close(hubUPC).join();
        channel.setStatus(ChannelStatus.CLOSED);
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);
        state = UPCState.fromContract(hubUPC.getUpc().getState().send());
        long secondsUntilWithdraw = BigInteger.valueOf(state.getExpiry()).add(BigInteger.valueOf(30)).subtract(BigInteger.valueOf(Instant.now().getEpochSecond())).longValue();
        logger.info("Seconds until withdraw: {}", secondsUntilWithdraw);

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.schedule(() -> {
            logger.info("Channel should be closed");
            UPCService.withdraw(hubUPC).join();
            logChannelView(hubUPCService, clientUPCService, channel.getId());
            channel.setStatus(ChannelStatus.CLOSED);
            hubDataService.updateChannel(channel);
            clientDataService.updateChannel(channel);
        }, secondsUntilWithdraw, TimeUnit.SECONDS).get();

        BigInteger hubBalance = hubToken.balanceOf(hub.getAddress()).send();
        BigInteger clientBalance = hubToken.balanceOf(client.getAddress()).send();
        logger.info("Token balance of Hub: {}", hubBalance);
        logger.info("Token balance of Client: {}", clientBalance);

        assertEquals(hubBalance.longValue(), 510L);
        assertEquals(clientBalance.longValue(), 490L);

    }

    @Test
    public void HTLC_Happy_Flow_With_Iterative_Withdraw() throws Exception {

        logger.info("INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        logger.info("CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), HTLCFlows.chainId, 120, hubToken.getContractAddress());
        Channel channel = hubDataService.createChannel(initChannel);

        UPC2.ChannelParams channelParams = channel.toContractParams();
        logger.info("Deploying channel");
        DecoratedUPC hubUPC = UPCService.createChannel(web3, hubTransactionManager, gasProvider, channelParams).join();
        channel.setAddress(hubUPC.getUpc().getContractAddress());
        logger.info("Deployed channel");

        hubDataService.updateChannel(channel);
        DecoratedUPC clientUPC = UPCService.loadUPCContract(web3, channel.getAddress(), clientTransactionManager, gasProvider);
        clientDataService.addChannel(channel);

        logger.info("DEPOSIT INTO CHANNEL");

        logger.info("Depositing into channel");
        CompletableFuture<TransactionReceipt> clientDepositFuture = clientToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
                        logger.info("Approve failed: {}", t.getMessage());
                        throw new CompletionException(t);
                    }
                    logger.info("Approve succeeded.");
                    return UPCService.deposit(clientUPC, 100).join();
                }
        );

        CompletableFuture<TransactionReceipt> hubDepositFuture = hubToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
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
        channel.addDeposit(hub.getAddress(), state.getHub().deposit.longValue());
        channel.addDeposit(client.getAddress(), state.getClient().deposit.longValue());
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        // 4) Client creates an HTLC promise to the hub
        byte[] secret1 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt1 = getSalt();
        HTLCConstructorParams params1 = new HTLCConstructorParams(10, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc1 = clientUPCService.createPromise(
                channel.getId(),
                1,
                HTLC.BINARY,
                params1,
                salt1);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc1, channel.getId(), HTLC.BINARY, params1, salt1)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, channel.getId());



        // 5) Client sends a receipt
        Receipt receipt1 = clientUPCService.createReceipt(channel.getId(), 10, Collections.singleton(htlc1.getPromiseId()));

        // Hub verifies the receipt and adds it
        if(!hubUPCService.checkReceipt(receipt1, channel.getId(), receipt1.getReceiptId(), 10, Collections.singleton(htlc1.getPromiseId()))){
            throw new RuntimeException("Receipt verify failed");
        }
        hubDataService.closeIncomingPromises(Collections.singletonList(htlc1));
        hubDataService.addIncomingReceipt(receipt1, receipt1.getReceiptId());
        channel.setCredit(hub.getAddress(), receipt1.getCumulativeCredit());
        hubDataService.updateChannel(channel);

        clientDataService.closeOutgoingPromises(Collections.singletonList(htlc1));
        clientDataService.addOutgoingReceipt(receipt1);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());


        UPCService.deployReceipt(hubUPC, receipt1).join();
        state = UPCState.fromContract(hubUPC.getUpc().getState().send());
        long secondsUntilWithdraw = BigInteger.valueOf(state.getExpiry()).add(BigInteger.valueOf(30)).subtract(BigInteger.valueOf(Instant.now().getEpochSecond())).longValue();
        logger.info("Seconds until withdraw: {}", secondsUntilWithdraw);

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.schedule(() -> {
            logger.info("Withdrawing {} from channel", 10);
            UPCService.withdrawNotClosing(hubUPC, 10).join();
        }, secondsUntilWithdraw, TimeUnit.SECONDS).get();

        state = UPCState.fromContract(hubUPC.getUpc().getState().send());
        channel.setClientDeposit(state.getClient().deposit.longValue());
        channel.setClientCredit(state.getClient().credit.longValue());
        channel.setPrevClientCredit(state.getClient().prevCredit.longValue());
        channel.setHubDeposit(state.getClient().deposit.longValue());
        channel.setHubCredit(state.getClient().credit.longValue());
        channel.setPrevHubCredit(state.getClient().prevCredit.longValue());
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);
        logChannelView(hubUPCService, clientUPCService, channel.getId());
        BigInteger hubBalance = hubToken.balanceOf(hub.getAddress()).send();
        BigInteger clientBalance = hubToken.balanceOf(client.getAddress()).send();
        logger.info("Token balance of Hub: {}", hubBalance);
        logger.info("Token balance of Client: {}", clientBalance);

        assertEquals(hubBalance.longValue(), 410L);
        assertEquals(clientBalance.longValue(), 400L);

        byte[] secret2 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt2 = getSalt();
        HTLCConstructorParams params2 = new HTLCConstructorParams(10, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc2 = clientUPCService.createPromise(
                channel.getId(),
                1,
                HTLC.BINARY,
                params2,
                salt2);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc2, channel.getId(), HTLC.BINARY, params2, salt2)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc2, htlc2.getPromiseId(), htlc2.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, channel.getId());



        // 5) Client sends a receipt
        Receipt receipt2 = clientUPCService.createReceipt(channel.getId(), 10, Collections.singleton(htlc2.getPromiseId()));

        // Hub verifies the receipt and adds it
        if(!hubUPCService.checkReceipt(receipt2, channel.getId(), receipt2.getReceiptId(), 10, Collections.singleton(htlc2.getPromiseId()))){
            throw new RuntimeException("Receipt verify failed");
        }
        hubDataService.closeIncomingPromises(Collections.singletonList(htlc2));
        hubDataService.addIncomingReceipt(receipt2, receipt2.getReceiptId());
        channel.setCredit(hub.getAddress(), receipt2.getCumulativeCredit());
        hubDataService.updateChannel(channel);

        clientDataService.closeOutgoingPromises(Collections.singletonList(htlc2));
        clientDataService.addOutgoingReceipt(receipt2);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        UPCService.deployReceipt(hubUPC, receipt2).join();

        UPCService.close(hubUPC).join();
        channel.setStatus(ChannelStatus.CLOSED);
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);
        state = UPCState.fromContract(hubUPC.getUpc().getState().send());
        secondsUntilWithdraw = BigInteger.valueOf(state.getExpiry()).add(BigInteger.valueOf(30)).subtract(BigInteger.valueOf(Instant.now().getEpochSecond())).longValue();
        logger.info("Seconds until withdraw: {}", secondsUntilWithdraw);

        scheduledExecutorService.schedule(() -> {
            logger.info("Channel should be closed");
            UPCService.withdraw(hubUPC).join();
            logChannelView(hubUPCService, clientUPCService, channel.getId());
            channel.setStatus(ChannelStatus.CLOSED);
            hubDataService.updateChannel(channel);
            clientDataService.updateChannel(channel);
        }, secondsUntilWithdraw, TimeUnit.SECONDS).get();

        hubBalance = hubToken.balanceOf(hub.getAddress()).send();
        clientBalance = hubToken.balanceOf(client.getAddress()).send();
        logger.info("Token balance of Hub: {}", hubBalance);
        logger.info("Token balance of Client: {}", clientBalance);

        assertEquals(hubBalance.longValue(), 520L);
        assertEquals(clientBalance.longValue(), 480L);

    }

    @Test
    public void HTLC_Deploy_Promise_Flow() throws Exception {

        logger.info("INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        logger.info("CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), HTLCFlows.chainId, 120, hubToken.getContractAddress());
        Channel channel = hubDataService.createChannel(initChannel);

        UPC2.ChannelParams channelParams = channel.toContractParams();
        logger.info("Deploying channel");
        DecoratedUPC hubUPC = UPCService.createChannel(web3, hubTransactionManager, gasProvider, channelParams).join();
        channel.setAddress(hubUPC.getUpc().getContractAddress());
        logger.info("Deployed channel");

        hubDataService.updateChannel(channel);
        DecoratedUPC clientUPC = UPCService.loadUPCContract(web3, channel.getAddress(), clientTransactionManager, gasProvider);
        clientDataService.addChannel(channel);

        logger.info("DEPOSIT INTO CHANNEL");

        logger.info("Depositing into channel");
        CompletableFuture<TransactionReceipt> clientDepositFuture = clientToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
                        logger.info("Approve failed: {}", t.getMessage());
                        throw new CompletionException(t);
                    }
                    logger.info("Approve succeeded.");
                    return UPCService.deposit(clientUPC, 100).join();
                }
        );

        CompletableFuture<TransactionReceipt> hubDepositFuture = hubToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
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
        channel.addDeposit(hub.getAddress(), state.getHub().deposit.longValue());
        channel.addDeposit(client.getAddress(), state.getClient().deposit.longValue());
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        // 4) Client creates an HTLC promise to the hub
        byte[] secret1 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt1 = getSalt();
        HTLCConstructorParams params1 = new HTLCConstructorParams(10, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc1 = clientUPCService.createPromise(
                channel.getId(),
                1,
                HTLC.BINARY,
                params1,
                salt1);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc1, channel.getId(), HTLC.BINARY, params1, salt1)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, channel.getId());



        // 5) Client sends a receipt
        Receipt receipt1 = clientUPCService.createReceipt(channel.getId(), 10, Collections.singleton(htlc1.getPromiseId()));

        // Hub verifies the receipt and adds it
        if(!hubUPCService.checkReceipt(receipt1, channel.getId(), receipt1.getReceiptId(), 10, Collections.singleton(htlc1.getPromiseId()))){
            throw new RuntimeException("Receipt verify failed");
        }
        hubDataService.closeIncomingPromises(Collections.singletonList(htlc1));
        hubDataService.addIncomingReceipt(receipt1, receipt1.getReceiptId());
        channel.setCredit(hub.getAddress(), receipt1.getCumulativeCredit());
        hubDataService.updateChannel(channel);

        clientDataService.closeOutgoingPromises(Collections.singletonList(htlc1));
        clientDataService.addOutgoingReceipt(receipt1);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        // 4) Client creates an HTLC promise to the hub
        byte[] secret2 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt2 = getSalt();
        HTLCConstructorParams params2 = new HTLCConstructorParams(10, Hash.sha256(secret2), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc2 = clientUPCService.createPromise(
                channel.getId(),
                1,
                HTLC.BINARY,
                params2,
                salt2);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc2, channel.getId(), HTLC.BINARY, params2, salt2)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc2, htlc2.getPromiseId(), htlc2.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        // Client never sends a receipt

        UPCService.deployReceipt(hubUPC, receipt1).join();
        UPCService.deployPromise(hubUPC, htlc2, Optional.of(receipt1), null).join();
        HTLC htlc2Promise = HTLC.load(htlc2.getAddress(), web3, hubTransactionManager, gasProvider);
        htlc2Promise.revealKey(secret2).send();
        UPCService.close(hubUPC).join();
        channel.setStatus(ChannelStatus.CLOSED);
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);
        state = UPCState.fromContract(hubUPC.getUpc().getState().send());
        long secondsUntilWithdraw = BigInteger.valueOf(state.getExpiry()).add(BigInteger.valueOf(30)).subtract(BigInteger.valueOf(Instant.now().getEpochSecond())).longValue();
        logger.info("Seconds until withdraw: {}", secondsUntilWithdraw);

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.schedule(() -> {
            logger.info("Channel should be closed");
            UPCService.withdraw(hubUPC).join();
            logChannelView(hubUPCService, clientUPCService, channel.getId());
            channel.setStatus(ChannelStatus.CLOSED);
            hubDataService.updateChannel(channel);
            clientDataService.updateChannel(channel);
        }, secondsUntilWithdraw, TimeUnit.SECONDS).get();

        BigInteger hubBalance = hubToken.balanceOf(hub.getAddress()).send();
        BigInteger clientBalance = hubToken.balanceOf(client.getAddress()).send();
        logger.info("Token balance of Hub: {}", hubBalance);
        logger.info("Token balance of Client: {}", clientBalance);

        assertEquals(hubBalance.longValue(), 520L);
        assertEquals(clientBalance.longValue(), 480L);

    }

    @Test
    public void HTLC_Deploy_Promise_Without_Receipt_Flow() throws Exception {

        logger.info("INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        logger.info("CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), HTLCFlows.chainId, 120, hubToken.getContractAddress());
        Channel channel = hubDataService.createChannel(initChannel);

        UPC2.ChannelParams channelParams = channel.toContractParams();
        logger.info("Deploying channel");
        DecoratedUPC hubUPC = UPCService.createChannel(web3, hubTransactionManager, gasProvider, channelParams).join();
        channel.setAddress(hubUPC.getUpc().getContractAddress());
        logger.info("Deployed channel");

        hubDataService.updateChannel(channel);
        DecoratedUPC clientUPC = UPCService.loadUPCContract(web3, channel.getAddress(), clientTransactionManager, gasProvider);
        clientDataService.addChannel(channel);

        logger.info("DEPOSIT INTO CHANNEL");

        logger.info("Depositing into channel");
        CompletableFuture<TransactionReceipt> clientDepositFuture = clientToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
                        logger.info("Approve failed: {}", t.getMessage());
                        throw new CompletionException(t);
                    }
                    logger.info("Approve succeeded.");
                    return UPCService.deposit(clientUPC, 100).join();
                }
        );

        CompletableFuture<TransactionReceipt> hubDepositFuture = hubToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
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
        channel.addDeposit(hub.getAddress(), state.getHub().deposit.longValue());
        channel.addDeposit(client.getAddress(), state.getClient().deposit.longValue());
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        // 4) Client creates an HTLC promise to the hub
        byte[] secret1 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt1 = getSalt();
        HTLCConstructorParams params1 = new HTLCConstructorParams(10, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc1 = clientUPCService.createPromise(
                channel.getId(),
                1,
                HTLC.BINARY,
                params1,
                salt1);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc1, channel.getId(), HTLC.BINARY, params1, salt1)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        // Client never sends a receipt

        UPCService.deployEmptyReceipt(hubUPC).join();
        UPCService.deployPromise(hubUPC, htlc1, Optional.empty(), null).join();
        HTLC htlc1Promise = HTLC.load(htlc1.getAddress(), web3, hubTransactionManager, gasProvider);
        htlc1Promise.revealKey(secret1).send();
        UPCService.close(hubUPC).join();
        channel.setStatus(ChannelStatus.CLOSED);
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);
        state = UPCState.fromContract(hubUPC.getUpc().getState().send());
        long secondsUntilWithdraw = BigInteger.valueOf(state.getExpiry()).add(BigInteger.valueOf(30)).subtract(BigInteger.valueOf(Instant.now().getEpochSecond())).longValue();
        logger.info("Seconds until withdraw: {}", secondsUntilWithdraw);

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.schedule(() -> {
            logger.info("Channel should be closed");
            UPCService.withdraw(hubUPC).join();
            logChannelView(hubUPCService, clientUPCService, channel.getId());
            channel.setStatus(ChannelStatus.CLOSED);
            hubDataService.updateChannel(channel);
            clientDataService.updateChannel(channel);
        }, secondsUntilWithdraw, TimeUnit.SECONDS).get();

        BigInteger hubBalance = hubToken.balanceOf(hub.getAddress()).send();
        BigInteger clientBalance = hubToken.balanceOf(client.getAddress()).send();
        logger.info("Token balance of Hub: {}", hubBalance);
        logger.info("Token balance of Client: {}", clientBalance);

        assertEquals(hubBalance.longValue(), 510L);
        assertEquals(clientBalance.longValue(), 490L);

    }

    @Test
    public void HTLC_Deploy_Promise_Flow_Out_Of_Order() throws Exception {

        logger.info("INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        logger.info("CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), HTLCFlows.chainId, 120, hubToken.getContractAddress());
        Channel channel = hubDataService.createChannel(initChannel);

        UPC2.ChannelParams channelParams = channel.toContractParams();
        logger.info("Deploying channel");
        DecoratedUPC hubUPC = UPCService.createChannel(web3, hubTransactionManager, gasProvider, channelParams).join();
        channel.setAddress(hubUPC.getUpc().getContractAddress());
        logger.info("Deployed channel");

        hubDataService.updateChannel(channel);
        DecoratedUPC clientUPC = UPCService.loadUPCContract(web3, channel.getAddress(), clientTransactionManager, gasProvider);
        clientDataService.addChannel(channel);

        logger.info("DEPOSIT INTO CHANNEL");

        logger.info("Depositing into channel");
        CompletableFuture<TransactionReceipt> clientDepositFuture = clientToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
                        logger.info("Approve failed: {}", t.getMessage());
                        throw new CompletionException(t);
                    }
                    logger.info("Approve succeeded.");
                    return UPCService.deposit(clientUPC, 100).join();
                }
        );

        CompletableFuture<TransactionReceipt> hubDepositFuture = hubToken.approve(channel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
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
        channel.addDeposit(hub.getAddress(), state.getHub().deposit.longValue());
        channel.addDeposit(client.getAddress(), state.getClient().deposit.longValue());
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        // 4) Client creates an HTLC promise to the hub
        byte[] secret1 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt1 = getSalt();
        HTLCConstructorParams params1 = new HTLCConstructorParams(10, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc1 = clientUPCService.createPromise(
                channel.getId(),
                1,
                HTLC.BINARY,
                params1,
                salt1);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc1, channel.getId(), HTLC.BINARY, params1, salt1)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, channel.getId());



        // 4) Client creates an HTLC promise to the hub
        byte[] secret2 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt2 = getSalt();
        HTLCConstructorParams params2 = new HTLCConstructorParams(10, Hash.sha256(secret2), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc2 = clientUPCService.createPromise(
                channel.getId(),
                1,
                HTLC.BINARY,
                params2,
                salt2);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc2, channel.getId(), HTLC.BINARY, params2, salt2)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc2, htlc2.getPromiseId(), htlc2.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, channel.getId());

        // 5) Client sends a receipt
        Receipt receipt2 = clientUPCService.createReceipt(channel.getId(), 10, Collections.singleton(htlc2.getPromiseId()));

        // Hub verifies the receipt and adds it
        if(!hubUPCService.checkReceipt(receipt2, channel.getId(), receipt2.getReceiptId(), 10, Collections.singleton(htlc2.getPromiseId()))){
            throw new RuntimeException("Receipt verify failed");
        }
        hubDataService.closeIncomingPromises(Collections.singletonList(htlc2));
        hubDataService.addIncomingReceipt(receipt2, receipt2.getReceiptId());
        channel.setCredit(hub.getAddress(), receipt2.getCumulativeCredit());
        hubDataService.updateChannel(channel);

        clientDataService.closeOutgoingPromises(Collections.singletonList(htlc1));
        clientDataService.addOutgoingReceipt(receipt2);
        clientDataService.updateChannel(channel);

        logChannelView(hubUPCService, clientUPCService, channel.getId());


        UPCService.deployReceipt(hubUPC, receipt2).join();
        UPCService.deployPromise(hubUPC, htlc1, Optional.of(receipt2), hubUPCService.getIncomingAccumulator(channel.getId())).join();
        HTLC htlc1Promise = HTLC.load(htlc1.getAddress(), web3, hubTransactionManager, gasProvider);
        htlc1Promise.revealKey(secret1).send();
        UPCService.close(hubUPC).join();
        channel.setStatus(ChannelStatus.CLOSED);
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);
        state = UPCState.fromContract(hubUPC.getUpc().getState().send());
        long secondsUntilWithdraw = BigInteger.valueOf(state.getExpiry()).add(BigInteger.valueOf(30)).subtract(BigInteger.valueOf(Instant.now().getEpochSecond())).longValue();
        logger.info("Seconds until withdraw: {}", secondsUntilWithdraw);

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.schedule(() -> {
            logger.info("Channel should be closed");
            UPCService.withdraw(hubUPC).join();
            logChannelView(hubUPCService, clientUPCService, channel.getId());
            channel.setStatus(ChannelStatus.CLOSED);
            hubDataService.updateChannel(channel);
            clientDataService.updateChannel(channel);
        }, secondsUntilWithdraw, TimeUnit.SECONDS).get();

        BigInteger hubBalance = hubToken.balanceOf(hub.getAddress()).send();
        BigInteger clientBalance = hubToken.balanceOf(client.getAddress()).send();
        logger.info("Token balance of Hub: {}", hubBalance);
        logger.info("Token balance of Client: {}", clientBalance);

        assertEquals(hubBalance.longValue(), 520L);
        assertEquals(clientBalance.longValue(), 480L);

    }

    @Test
    public void HTLC_Event_Flow() throws Exception {

        logger.info("INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        UPCEventHandler hubEventHandler = new DefaultUPCEventHandler(new UPCHandledEvents(), hubDataService, hubDataService, hubDataService);
        ChannelWatcher hubChannelWatcher = new ChannelWatcher(web3, hubEventHandler);

        UPCEventHandler clientEventHandler = new DefaultUPCEventHandler(new UPCHandledEvents(), clientDataService, clientDataService, clientDataService);
        ChannelWatcher clientChannelWatcher = new ChannelWatcher(web3, clientEventHandler);

        logger.info("CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), HTLCFlows.chainId, 120, hubToken.getContractAddress());
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

        hubChannelWatcher.setChannels(Collections.singletonList(hubChannel.getAddress()));
        clientChannelWatcher.setChannels(Collections.singletonList(clientChannel.getAddress()));

        logChannelView(hubUPCService, clientUPCService, hubChannel.getId());

        logger.info("DEPOSIT INTO CHANNEL");

        logger.info("Depositing into channel");
        CompletableFuture<TransactionReceipt> clientDepositFuture = clientToken.approve(clientChannel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
                        logger.info("Approve failed: {}", t.getMessage());
                        throw new CompletionException(t);
                    }
                    logger.info("Approve succeeded.");
                    return UPCService.deposit(clientUPC, 100).join();
                }
        );


        CompletableFuture<TransactionReceipt> hubDepositFuture = hubToken.approve(hubChannel.getAddress(), BigInteger.valueOf(100)).sendAsync().handle(
                (TransactionReceipt r, Throwable t) -> {
                    if(t != null){
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
        byte[] secret1 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt1 = getSalt();
        HTLCConstructorParams params1 = new HTLCConstructorParams(10, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc1 = clientUPCService.createPromise(
                clientChannel.getId(),
                1,
                HTLC.BINARY,
                params1,
                salt1);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc1, hubChannel.getId(), HTLC.BINARY, params1, salt1)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, hubChannel.getId());



        // 5) Client sends a receipt
        Receipt receipt1 = clientUPCService.createReceipt(clientChannel.getId(), 10, Collections.singleton(htlc1.getPromiseId()));

        // Hub verifies the receipt and adds it
        if(!hubUPCService.checkReceipt(receipt1, hubChannel.getId(), receipt1.getReceiptId(), 10, Collections.singleton(htlc1.getPromiseId()))){
            throw new RuntimeException("Receipt verify failed");
        }
        hubDataService.closeIncomingPromises(Collections.singletonList(htlc1));
        hubDataService.addIncomingReceipt(receipt1, receipt1.getReceiptId());
        hubChannel.setHubCredit(receipt1.getCumulativeCredit());
        hubDataService.updateChannel(hubChannel);

        clientDataService.closeOutgoingPromises(Collections.singletonList(htlc1));
        clientDataService.addOutgoingReceipt(receipt1);
        clientChannel.setHubCredit(receipt1.getCumulativeCredit());
        clientDataService.updateChannel(clientChannel);

        logChannelView(hubUPCService, clientUPCService, hubChannel.getId());

        // 4) Client creates an HTLC promise to the hub
        byte[] secret2 = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        byte[] salt2 = getSalt();
        HTLCConstructorParams params2 = new HTLCConstructorParams(10, Hash.sha256(secret2), Instant.now().getEpochSecond() + 300);
        StatefulPromise htlc2 = clientUPCService.createPromise(
                clientChannel.getId(),
                1,
                HTLC.BINARY,
                params2,
                salt2);

        // Hub verifies the promise and adds it
        if(!hubUPCService.checkPromise(htlc2, hubChannel.getId(), HTLC.BINARY, params2, salt2)){
            throw new RuntimeException("Promise verify failed");
        }
        hubDataService.addIncomingPromise(htlc2, htlc2.getPromiseId(), htlc2.getPromiseType(), true);

        logChannelView(hubUPCService, clientUPCService, hubChannel.getId());

        // Client never sends a receipt
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

        assertEquals(hubBalance.longValue(), 520L);
        assertEquals(clientBalance.longValue(), 480L);

    }


}
