package com.visa.vr.upc.sdk.unit;

import com.visa.vr.upc.sdk.custody.BasicSigner;
import com.visa.vr.upc.sdk.custody.ISigner;
import com.visa.vr.upc.sdk.MerkleAccumulator;
import com.visa.vr.upc.sdk.PromiseService;
import com.visa.vr.upc.sdk.domain.HTLCConstructorParams;
import com.visa.vr.upc.sdk.domain.Promise;
import com.visa.vr.upc.sdk.domain.Receipt;
import com.visa.vr.upc.sdk.generated.HTLC;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.crypto.ECKeyPair;
import org.web3j.utils.Numeric;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;


public class PromiseServiceTest {

    private static byte[] getHTLCSecret(){
        byte[] secret = Numeric.hexStringToByteArray("0xdeadbeefdeafbeefdeadbeefdeadbeefdeadbeefdeafbeefdeadbeefdeadbeef");
        assertEquals(secret.length, 32);
        return secret;
    }

    private static String getAddress(){
        return "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
    }

    private static List<Promise> getPromises(){
        Promise p1 = new Promise();
        p1.setAddress("0xe089034a708fb6d9198c82f48f82c9f9d1dcbda4");

        Promise p2 = new Promise();
        p2.setAddress("0xcd7f4ba0fa5ff59a21cfc8c75e8f9a9c2b24e26d");

        Promise p3 = new Promise();
        p3.setAddress("0x95f5a81d4ea8ac507408be170b7d6f2fe7d7e5fc");

        return Arrays.asList(p1, p2, p3);
    }

    private static HTLCConstructorParams getHTLCParams() throws NoSuchAlgorithmException {
        byte[] secret = getHTLCSecret();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(secret);
        HTLCConstructorParams params = new HTLCConstructorParams(20, hash, 100);
        return params;
    }

    private static Stream<Arguments> getPrivateKeysAsArguments(){
        return getPrivateKeys().stream().map(s -> Arguments.arguments(s));
    }

    private static List<String> getPrivateKeys(){
        return Arrays.asList(
                "20C52C16E0813E342E4E2A700A1EEB104F11A4634B3B4839F822E044D63BD2F0",
                "36A110380859D05E60C2991479DE4D24DA3116A49B342127C1F1F7D536CAE547",
                "EBF789FB8DC37E188AE746A71212720DF1F163715F38B194A3CEE994E3E563D4",
                "FE4EAEFBE4076F4D726ABD8B20335543E4210524AD3DF189C2BBF3A756702FFE",
                "9E32F44504F253A2D349507E17355A035A090104F14485C5DCADDEF85E6560D5");
    }

    @ParameterizedTest
    @MethodSource("getPrivateKeysAsArguments")
    void signedPromiseCanBeVerified(String privateKey) throws NoSuchAlgorithmException, SignatureException {
        long channelId = 1;
        long chainId = 0;
        long receiptId = 5;
        String channelAddress = getAddress();

        ECKeyPair keyPair = ECKeyPair.create(Numeric.hexStringToByteArray(privateKey));
        ISigner signer = new BasicSigner(keyPair);
        String sender = signer.getAddress();
        String receiver = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
        HTLCConstructorParams params = getHTLCParams();

        byte[] salt = getHTLCSecret();

        Promise promise = PromiseService.createPromise(channelId, chainId, channelAddress, sender, receiver, receiptId, HTLC.BINARY, params, salt);
        promise = PromiseService.signPromise(promise, signer);
        assertTrue(PromiseService.verifyPromise(promise, channelId, chainId, channelAddress, sender, receiver, receiptId, HTLC.BINARY, params, salt));
    }

    @ParameterizedTest
    @MethodSource("getPrivateKeysAsArguments")
    void signedReceiptCanBeVerified(String privateKey) throws SignatureException {
        long channelId = 1;
        long chainId = 0;
        long receiptId = 2;
        long credit = 5;
        byte[] accumulatorRoot = MerkleAccumulator.keyFromAddress(getAddress());

        ECKeyPair keyPair = ECKeyPair.create(Numeric.hexStringToByteArray(privateKey));
        ISigner signer = new BasicSigner(keyPair);
        String sender = signer.getAddress();
        String receiver = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

        Receipt receipt = PromiseService.createReceipt(channelId, chainId, sender, receiver, receiptId, credit, accumulatorRoot);
        receipt = PromiseService.signReceipt(receipt, signer);
        assertTrue(PromiseService.verifyReceipt(receipt, channelId, chainId, sender, receiver, receiptId, credit, accumulatorRoot));
    }

    @ParameterizedTest
    @MethodSource("getPrivateKeysAsArguments")
    void signedReceiptCanBeVerifiedFromPromises(String privateKey) throws SignatureException {
        long channelId = 1;
        long chainId = 0;
        long credit = 5;
        long receiptId = 2;

        byte[] accumulatorRoot = MerkleAccumulator.fromAddresses(getPromises().stream().map(p -> p.getAddress()).collect(Collectors.toList())).getRootHash();

        ECKeyPair keyPair = ECKeyPair.create(Numeric.hexStringToByteArray(privateKey));
        ISigner signer = new BasicSigner(keyPair);
        String sender = signer.getAddress();
        String receiver = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

        Receipt receipt = PromiseService.createReceipt(channelId, chainId, sender, receiver, receiptId, credit, accumulatorRoot);
        receipt = PromiseService.signReceipt(receipt, signer);
        assertTrue(PromiseService.verifyReceipt(receipt, channelId, chainId, sender, receiver, receiptId, credit, accumulatorRoot));
    }

    @ParameterizedTest
    @MethodSource("getPrivateKeysAsArguments")
    void signedReceiptCanBeVerifiedFromPromisesWithNoPromises(String privateKey) throws SignatureException {
        long channelId = 1;
        long chainId = 0;
        long receiptId = 2;
        long credit = 5;

        byte[] accumulatorRoot = new MerkleAccumulator(new ArrayList<byte[]>()).getRootHash();

        ECKeyPair keyPair = ECKeyPair.create(Numeric.hexStringToByteArray(privateKey));
        ISigner signer = new BasicSigner(keyPair);
        String sender = signer.getAddress();
        String receiver = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

        Receipt receipt = PromiseService.createReceipt(channelId, chainId, sender, receiver, receiptId, credit, accumulatorRoot);
        receipt = PromiseService.signReceipt(receipt, signer);
        assertTrue(PromiseService.verifyReceipt(receipt, channelId, chainId, sender, receiver, receiptId, credit, accumulatorRoot));
    }




}