package com.visa.vr.upc.sdk.e2e;

import com.visa.vr.upc.sdk.DefaultDataService;
import com.visa.vr.upc.sdk.StatefulUPCService;
import com.visa.vr.upc.sdk.custody.BasicSigner;
import com.visa.vr.upc.sdk.custody.ISigner;
import com.visa.vr.upc.sdk.domain.*;
import com.visa.vr.upc.sdk.generated.ERC20PresetFixedSupply;
import com.visa.vr.upc.sdk.generated.HTLC;
import com.visa.vr.upc.sdk.unit.UPCConsistencyTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
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

public class HTLCPerformance {
    private static final long AMOUNT_PER_PAYMENT = 10;
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

    public static String getRandomAddress(){
        byte[] address = new byte[20];
        random.nextBytes(address);
        return Numeric.toHexString(address);
    }

    public static String getNewPrivateKey(){
        return Numeric.toHexString(getSalt());
    }

    @BeforeAll
    public static void setupAll() throws IOException {
        Properties properties = new Properties();
        properties.load(UPCConsistencyTest.class.getResourceAsStream("/test.properties"));
        String nodeUrl = properties.getProperty("nodeUrl");
        HTLCPerformance.logger = LoggerFactory.getLogger(HTLCFlows.class);
        HTLCPerformance.random = new Random();
        HTLCPerformance.web3 = Web3j.build(new HttpService(nodeUrl));
        HTLCPerformance.chainId = HTLCPerformance.web3.ethChainId().getId();
        HTLCPerformance.gasProvider = getFreeGasProvider(web3);
    }

    @BeforeEach
    public void setupEach() throws Exception {
        this.hubCreds = Credentials.create(getNewPrivateKey());
        this.hub = new BasicSigner(hubCreds.getEcKeyPair());
        this.hubTransactionManager = new FastRawTransactionManager(web3, hubCreds);


        this.clientCreds = Credentials.create(getNewPrivateKey());
        this.client = new BasicSigner(clientCreds.getEcKeyPair());
        this.clientTransactionManager = new FastRawTransactionManager(web3, clientCreds);

    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000})
    public void HTLC_One_At_A_Time(int n) throws Exception {
        logger.info("INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        logger.info("CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), HTLCPerformance.chainId, 120, hubToken.getContractAddress());
        Channel channel = hubDataService.createChannel(initChannel);

        channel.setAddress(getRandomAddress());
        hubDataService.updateChannel(channel);
        clientDataService.addChannel(channel);

        channel.addDeposit(hub.getAddress(), AMOUNT_PER_PAYMENT * n);
        channel.addDeposit(client.getAddress(), AMOUNT_PER_PAYMENT * n);
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);

        logger.info("Testing {} htlcs, closing each before the next one", n);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            // 4) Client creates an HTLC promise to the hub
            byte[] secret1 = getSalt();
            byte[] salt1 = getSalt();
            HTLCConstructorParams params1 = new HTLCConstructorParams(AMOUNT_PER_PAYMENT, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
            StatefulPromise htlc1 = clientUPCService.createPromise(
                    channel.getId(),
                    1,
                    HTLC.BINARY,
                    params1,
                    salt1);

            // Hub verifies the promise and adds it
            if (!hubUPCService.checkPromise(htlc1, channel.getId(), HTLC.BINARY, params1, salt1)) {
                throw new RuntimeException("Promise verify failed");
            }
            hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);


            // 5) Client sends a receipt
            Receipt receipt1 = clientUPCService.createReceipt(channel.getId(), AMOUNT_PER_PAYMENT, Collections.singleton(htlc1.getPromiseId()));

            // Hub verifies the receipt and adds it
            if (!hubUPCService.checkReceipt(receipt1, channel.getId(), receipt1.getReceiptId(), AMOUNT_PER_PAYMENT, Collections.singleton(htlc1.getPromiseId()))) {
                throw new RuntimeException("Receipt verify failed");
            }
            hubDataService.closeIncomingPromises(Collections.singletonList(htlc1));
            hubDataService.addIncomingReceipt(receipt1, receipt1.getReceiptId());
            channel.setCredit(hub.getAddress(), receipt1.getCumulativeCredit());
            hubDataService.updateChannel(channel);

            clientDataService.closeOutgoingPromises(Collections.singletonList(htlc1));
            clientDataService.addOutgoingReceipt(receipt1);
            clientDataService.updateChannel(channel);
        }
        long endTime = System.currentTimeMillis();
        logger.info("Elapsed Time in Milliseconds: {}", endTime - startTime);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000})
    public void HTLC_Close_In_Order(int n) throws Exception {
        logger.info("INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        logger.info("CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), HTLCPerformance.chainId, 120, hubToken.getContractAddress());
        Channel channel = hubDataService.createChannel(initChannel);

        channel.setAddress(getRandomAddress());
        hubDataService.updateChannel(channel);
        clientDataService.addChannel(channel);

        channel.addDeposit(hub.getAddress(), AMOUNT_PER_PAYMENT * n);
        channel.addDeposit(client.getAddress(), AMOUNT_PER_PAYMENT * n);
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);

        ArrayList<Long> ids = new ArrayList<>(n);

        logger.info("Testing {} htlcs, closing each before the next one", n);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            // 4) Client creates an HTLC promise to the hub
            byte[] secret1 = getSalt();
            byte[] salt1 = getSalt();
            HTLCConstructorParams params1 = new HTLCConstructorParams(AMOUNT_PER_PAYMENT, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
            StatefulPromise htlc1 = clientUPCService.createPromise(
                    channel.getId(),
                    1,
                    HTLC.BINARY,
                    params1,
                    salt1);
            ids.add(htlc1.getPromiseId());

            // Hub verifies the promise and adds it
            if (!hubUPCService.checkPromise(htlc1, channel.getId(), HTLC.BINARY, params1, salt1)) {
                throw new RuntimeException("Promise verify failed");
            }
            hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);
        }
        for (int i = 0; i < n; i++) {
            // 5) Client sends a receipt
            StatefulPromise htlc1 = hubDataService.getIncomingPromiseById(ids.get(i)).get();

            Receipt receipt1 = clientUPCService.createReceipt(channel.getId(), AMOUNT_PER_PAYMENT, Collections.singleton(htlc1.getPromiseId()));

            // Hub verifies the receipt and adds it
            if (!hubUPCService.checkReceipt(receipt1, channel.getId(), receipt1.getReceiptId(), AMOUNT_PER_PAYMENT, Collections.singleton(htlc1.getPromiseId()))) {
                throw new RuntimeException("Receipt verify failed");
            }
            hubDataService.closeIncomingPromises(Collections.singletonList(htlc1));
            hubDataService.addIncomingReceipt(receipt1, receipt1.getReceiptId());
            channel.setCredit(hub.getAddress(), receipt1.getCumulativeCredit());
            hubDataService.updateChannel(channel);

            clientDataService.closeOutgoingPromises(Collections.singletonList(htlc1));
            clientDataService.addOutgoingReceipt(receipt1);
            clientDataService.updateChannel(channel);
        }
        long endTime = System.currentTimeMillis();
        logger.info("Elapsed Time in Milliseconds: {}", endTime - startTime);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000})
    public void HTLC_Close_In_Random_Order(int n) throws Exception {
        logger.info("INITIALIZE SERVICES FOR BOTH HUB AND CLIENT");
        DefaultDataService clientDataService = new DefaultDataService(client.getAddress());
        DefaultDataService hubDataService = new DefaultDataService(hub.getAddress());

        StatefulUPCService clientUPCService = new StatefulUPCService(client, clientDataService, clientDataService, clientDataService);
        StatefulUPCService hubUPCService = new StatefulUPCService(hub, hubDataService, hubDataService, hubDataService);

        logger.info("CREATE A CHANNEL");
        Channel initChannel = new Channel(hub.getAddress(), client.getAddress(), HTLCPerformance.chainId, 120, hubToken.getContractAddress());
        Channel channel = hubDataService.createChannel(initChannel);

        channel.setAddress(getRandomAddress());
        hubDataService.updateChannel(channel);
        clientDataService.addChannel(channel);

        channel.addDeposit(hub.getAddress(), AMOUNT_PER_PAYMENT * n);
        channel.addDeposit(client.getAddress(), AMOUNT_PER_PAYMENT * n);
        hubDataService.updateChannel(channel);
        clientDataService.updateChannel(channel);

        ArrayList<Long> ids = new ArrayList<>(n);

        logger.info("Testing {} htlcs, closing each before the next one", n);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            // 4) Client creates an HTLC promise to the hub
            byte[] secret1 = getSalt();
            byte[] salt1 = getSalt();
            HTLCConstructorParams params1 = new HTLCConstructorParams(AMOUNT_PER_PAYMENT, Hash.sha256(secret1), Instant.now().getEpochSecond() + 300);
            StatefulPromise htlc1 = clientUPCService.createPromise(
                    channel.getId(),
                    1,
                    HTLC.BINARY,
                    params1,
                    salt1);
            ids.add(htlc1.getPromiseId());

            // Hub verifies the promise and adds it
            if (!hubUPCService.checkPromise(htlc1, channel.getId(), HTLC.BINARY, params1, salt1)) {
                throw new RuntimeException("Promise verify failed");
            }
            hubDataService.addIncomingPromise(htlc1, htlc1.getPromiseId(), htlc1.getPromiseType(), true);
        }
        Collections.shuffle(ids);
        for (int i = 0; i < n; i++) {
            // 5) Client sends a receipt
            StatefulPromise htlc1 = hubDataService.getIncomingPromiseById(ids.get(i)).get();

            Receipt receipt1 = clientUPCService.createReceipt(channel.getId(), AMOUNT_PER_PAYMENT, Collections.singleton(htlc1.getPromiseId()));

            // Hub verifies the receipt and adds it
            if (!hubUPCService.checkReceipt(receipt1, channel.getId(), receipt1.getReceiptId(), AMOUNT_PER_PAYMENT, Collections.singleton(htlc1.getPromiseId()))) {
                throw new RuntimeException("Receipt verify failed");
            }
            hubDataService.closeIncomingPromises(Collections.singletonList(htlc1));
            hubDataService.addIncomingReceipt(receipt1, receipt1.getReceiptId());
            channel.setCredit(hub.getAddress(), receipt1.getCumulativeCredit());
            hubDataService.updateChannel(channel);

            clientDataService.closeOutgoingPromises(Collections.singletonList(htlc1));
            clientDataService.addOutgoingReceipt(receipt1);
            clientDataService.updateChannel(channel);
        }
        long endTime = System.currentTimeMillis();
        logger.info("Elapsed Time in Milliseconds: {}", endTime - startTime);
    }
}
