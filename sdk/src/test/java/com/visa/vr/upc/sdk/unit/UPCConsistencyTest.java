package com.visa.vr.upc.sdk.unit;

import com.visa.vr.upc.sdk.MerkleAccumulator;
import com.visa.vr.upc.sdk.custody.BasicSigner;
import com.visa.vr.upc.sdk.custody.ISigner;
import com.visa.vr.upc.sdk.PromiseService;
import com.visa.vr.upc.sdk.domain.*;
import com.visa.vr.upc.sdk.generated.ERC20PresetFixedSupply;
import com.visa.vr.upc.sdk.generated.HTLC;
import com.visa.vr.upc.sdk.generated.UPC2;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.junit.jupiter.api.Assertions.*;

public class UPCConsistencyTest {

    private static Web3j web3;

    private static ContractGasProvider gasProvider;

    private TransactionManager transactionManager;

    private UPC2 upc;

    private ERC20PresetFixedSupply token;

    private final long channelId = 1;

    private final long chainId = 0;

    private String hub;

    private String client;

    private ISigner signer;

    public static ContractGasProvider getFreeGasProvider(Web3j web3) throws IOException {
        BigInteger gasLimit = web3.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getGasLimit();
        return new StaticGasProvider(BigInteger.ZERO, gasLimit);
    }

    @BeforeAll
    static void setUpAll() throws IOException {
        Properties properties = new Properties();
        properties.load(UPCConsistencyTest.class.getResourceAsStream("/test.properties"));
        String nodeUrl = properties.getProperty("nodeUrl");
        web3 = Web3j.build(new HttpService(nodeUrl));
        gasProvider = getFreeGasProvider(web3);
    }

    @BeforeEach
    void setUpEach() throws Exception {
        Credentials masterCreds = Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");
        transactionManager = new RawTransactionManager(web3, masterCreds);

        hub = masterCreds.getAddress();
        signer = new BasicSigner(ECKeyPair.create(Numeric.hexStringToByteArray("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63")));
        client = getAddress();
        token = ERC20PresetFixedSupply.deploy(web3, transactionManager, gasProvider, "Token", "T", BigInteger.valueOf(1000), hub).send();
        UPC2.ChannelParams channelParams = new UPC2.ChannelParams(BigInteger.valueOf(channelId), BigInteger.valueOf(chainId), hub, client, BigInteger.valueOf(100), token.getContractAddress());
        upc = UPC2.deploy(web3, transactionManager, gasProvider, channelParams).send();


    }

    private static Stream<Arguments> getPrivateKeys(){
        return Stream.of(
                arguments("20C52C16E0813E342E4E2A700A1EEB104F11A4634B3B4839F822E044D63BD2F0"),
                arguments("36A110380859D05E60C2991479DE4D24DA3116A49B342127C1F1F7D536CAE547"),
                arguments("EBF789FB8DC37E188AE746A71212720DF1F163715F38B194A3CEE994E3E563D4"),
                arguments("FE4EAEFBE4076F4D726ABD8B20335543E4210524AD3DF189C2BBF3A756702FFE"),
                arguments("9E32F44504F253A2D349507E17355A035A090104F14485C5DCADDEF85E6560D5")
        );
    }

    private static List<String> getAddresses(){
        return Arrays.asList(new String[]{
                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                "0xe7E6c88Ad1BAb6508a251B7995f44fB1C5E3dCF7",
                "0xbd5b556ddf1f97e2ac7c842b847be1b5edcc4c50",
                "0x0e1a29f037c531b3b2ae5c912b7f972964231f1c",
                "0x4257b06b6b739db2b2d8b5ab4c94852021221506",
                "0x41eb5803b08506e1b2be6126bc013cf5130a3306",
                "0xbfa95d187cb3511b2e959212d4723bf40de7e899",
                "0xda5d1892a90b80775ce9bf06adcba1b77bbe9a17",
                "0x30f7dce9cf95568b30a88249851d4cedb297daea",
                "0xcd7f4ba0fa5ff59a21cfc8c75e8f9a9c2b24e26d",
                "0xe089034a708fb6d9198c82f48f82c9f9d1dcbda4",
                "0x95f5a81d4ea8ac507408be170b7d6f2fe7d7e5fc",
                "0xc740a760e5e0e69598c84f3f1aca01713e3e1876",
                "0x51e675e43ebe7551f3e6cc0a1e5a521a7482e4bf",
                "0x9ef7f8b332945fc5fed03c0a1d290ee831938d3e",
                "0xd70b69c4a166a0a771ac838ed0eed5f2fe3f35c2"
        });
    }

    private static Stream<Arguments> getAddressLists(){
        List<String> addresses = getAddresses();
        return IntStream.range(1, addresses.size()).mapToObj(i -> Arguments.arguments(addresses.subList(0, i)));
    }

    private static Stream<Arguments> getAddressSample(){
        List<String> addresses = getAddresses();
        return IntStream.range(0, addresses.size()).mapToObj(i -> Arguments.arguments(i, addresses.get(i)));
    }

    private static Stream<Arguments> getSalts(){
        return getPrivateKeys();
    }

    private static byte[] getHTLCSecret(){
        byte[] secret = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        assertEquals(secret.length, 32);
        return secret;
    }

    private static String getAddress(){
        return "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
    }

    private static HTLCConstructorParams getHTLCParams() throws NoSuchAlgorithmException {
        byte[] secret = getHTLCSecret();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(secret);
        HTLCConstructorParams params = new HTLCConstructorParams(20, hash, 100);
        return params;
    }

    @ParameterizedTest
    @MethodSource("getPrivateKeys")
    void signingConsistentWithContract(String privateKey) throws Exception {
        ECKeyPair keyPair = ECKeyPair.create(Numeric.hexStringToByteArray(privateKey));
        ISigner signer = new BasicSigner(keyPair);

        String message = "hello";
        byte[] messageBytes = message.getBytes();
        byte[] hash = Hash.sha3(messageBytes);
        Sign.SignatureData signature = signer.signPrefixedMessage(hash);

        assertTrue(upc._sigVerify(signer.getAddress(), hash, new Signature(signature).toContractSignature()).send());
    }

    @ParameterizedTest
    @MethodSource("getPrivateKeys")
    void receiptHashConsistentWithContract(String privateKey) throws Exception {
        Credentials userCreds = Credentials.create(ECKeyPair.create(Numeric.hexStringToByteArray(privateKey)));
        byte[] accumulatorRoot = getHTLCSecret();
        Receipt receipt = PromiseService.createReceipt(channelId, chainId, hub, userCreds.getAddress(), 1, 10, accumulatorRoot);
        byte[] hash = PromiseService.hashReceipt(receipt);
        byte[] hash2 = upc._receiptHash(receipt.toContractReceipt()).send();
        assertArrayEquals(hash, hash2);
    }

    @ParameterizedTest
    @MethodSource("getPrivateKeys")
    void promiseHashConsistentWithContract(String privateKey) throws Exception {
        Credentials userCreds = Credentials.create(ECKeyPair.create(Numeric.hexStringToByteArray(privateKey)));
        byte[] salt = getHTLCSecret();
        String bytecode = HTLC.BINARY;
        HTLCConstructorParams params = getHTLCParams();
        Promise promise = PromiseService.createPromise(channelId, chainId, upc.getContractAddress(), hub, userCreds.getAddress(), 2, bytecode, params, salt);
        byte[] hash = PromiseService.hashPromise(promise);
        byte[] hash2 = upc._promiseHash(promise.toContractPromise(), promise.getAddress()).send();
        assertArrayEquals(hash, hash2);
    }

    @ParameterizedTest
    @MethodSource("getSalts")
    void promiseAddressConsistentWithContract(String saltHex) throws Exception {
        byte[] salt = Numeric.hexStringToByteArray(saltHex);
        String bytecode = HTLC.BINARY;
        HTLCConstructorParams params = getHTLCParams();
        String combinedBytecode = PromiseService.combineBytecode(bytecode, params);
        String upcAddress = upc.getContractAddress();
        String address = PromiseService.getCreate2Address(upcAddress, salt, combinedBytecode);
        String address2 = upc.getAddress(Numeric.hexStringToByteArray(combinedBytecode), Numeric.toBigInt(saltHex)).send();
        System.out.println(address);
        System.out.println(address2);
        assertEquals(address, address2);
    }

    @ParameterizedTest
    @MethodSource("getAddressSample")
    void validAccumulatorProofConsistentWithContract(int i, String toFind) throws Exception {
        List<String> addresses = getAddresses();
        MerkleAccumulator acc = MerkleAccumulator.fromAddresses(addresses);
        byte[] proof = acc.getInclusionProof(MerkleAccumulator.keyFromAddress(toFind));
        ArrayList<byte[]> proofList = new ArrayList<>();
        for(int j = 0; j < proof.length; j+= 32){
            proofList.add(Arrays.copyOfRange(proof, j, j + 32));
        }

        assertTrue(upc.checkProof(proofList, acc.getRootHash(), MerkleAccumulator.keyFromAddress(toFind)).send().booleanValue());
    }

    @ParameterizedTest
    @MethodSource("getAddressSample")
    void invalidAccumulatorProofConsistentWithContract(int i, String toFind) throws Exception {
        List<String> addresses = getAddresses();
        MerkleAccumulator acc = MerkleAccumulator.fromAddresses(addresses);
        byte[] proof = acc.getInclusionProof(MerkleAccumulator.keyFromAddress(toFind));

        ArrayList<byte[]> proofList = new ArrayList<>();
        for(int j = 0; j < proof.length; j+= 32){
            proofList.add(Arrays.copyOfRange(proof, j, j + 32));
        }

        assertFalse(upc.checkProof(proofList, acc.getRootHash(), MerkleAccumulator.keyFromAddress(addresses.get((i + 1) % addresses.size()))).send().booleanValue());
    }

    @Test
    void createdPromiseConsistentWithContract() throws Exception {
        long receiptId = 0;
        String channelAddress = upc.getContractAddress();

        String sender = signer.getAddress();
        String receiver = client;
        HTLCConstructorParams params = getHTLCParams();

        byte[] salt = getHTLCSecret();

        Promise promise = PromiseService.createPromise(channelId, chainId, channelAddress, sender, receiver, receiptId, HTLC.BINARY, params, salt);
        promise = PromiseService.signPromise(promise, signer);
        upc.verifyPromise(promise.toContractPromise(), promise.getSignature().toContractSignature(), Collections.singletonList(salt), new UPC2.Party(client, BigInteger.TEN, BigInteger.ZERO, BigInteger.TEN, BigInteger.ZERO, BigInteger.ZERO, true, false, false, salt)).send();
    }

    @Test
    void createdReceiptConsistentWithContract() throws Exception {
        String sender = signer.getAddress();
        String receiver = client;
        long receiptId = 1;
        long credit = 10;
        byte[] accRoot = getHTLCSecret();

        Receipt receipt = PromiseService.createReceipt(channelId, chainId, sender, receiver, receiptId, credit, accRoot);
        receipt = PromiseService.signReceipt(receipt, signer);
        upc.verifyReceipt(receipt.toContractReceipt(), receipt.getSignature().toContractSignature(), client).send();
    }
}
