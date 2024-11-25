package com.visa.vr.upc.sdk.unit;

import com.visa.vr.upc.sdk.MerkleAccumulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.abi.datatypes.Address;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;


public class MerkleAccumulatorTest {

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
        return IntStream.range(0, addresses.size()).mapToObj(i -> Arguments.arguments(addresses.get(i)));
    }

    private static Stream<Arguments> getInvalidAddresses(){
        return Arrays.asList(new String[]{
                "0xd70b69c4a166a0a771ac838ed0eed5f2fe3f35cq",
                "e675e43ebe7551f3e6cc0a1e5a521a7482e4bf",
                "",
                "0x95f5a81d4ea8ac507408be170b7d6f2fe7d7e5fc4342"
        }).stream().map(s -> Arguments.arguments(Arrays.asList(s)));
    }

    @ParameterizedTest
    @MethodSource("getInvalidAddresses")
    void newAccumulatorWithInvalidAddressesFails(List<String> addresses){
        assertThrows(IllegalArgumentException.class, () -> MerkleAccumulator.fromAddresses(addresses));
    }

    @ParameterizedTest
    @MethodSource("getAddressLists")
    void getInclusionProofVerifiesWithDifferentSizedLists(List<String> addresses) throws IOException {
        String toFind = addresses.get(0);
        MerkleAccumulator acc = MerkleAccumulator.fromAddresses(addresses);
        byte[] proof = acc.getInclusionProof(MerkleAccumulator.keyFromAddress(toFind));
        assertTrue(MerkleAccumulator.verifyInclusionProof(acc.getRootHash(), MerkleAccumulator.keyFromAddress(toFind), proof));
    }

    @ParameterizedTest
    @MethodSource("getAddressSample")
    void getInclusionProofVerifiesWithDifferentIndexes(String toFind) throws IOException {
        List<String> addresses = getAddresses();
        MerkleAccumulator acc = MerkleAccumulator.fromAddresses(addresses);
        byte[] proof = acc.getInclusionProof(MerkleAccumulator.keyFromAddress(toFind));
        assertTrue(MerkleAccumulator.verifyInclusionProof(acc.getRootHash(), MerkleAccumulator.keyFromAddress(toFind), proof));
    }

    @Test
    void getInclusionProofFailsWithMissingItem() throws IOException {
        String toFind = Address.DEFAULT.toString();
        List<String> addresses = getAddresses();
        assertTrue(addresses.indexOf(toFind) == -1);
        MerkleAccumulator acc = MerkleAccumulator.fromAddresses(addresses);
        assertThrows(NoSuchElementException.class, () -> acc.getInclusionProof(MerkleAccumulator.keyFromAddress(toFind)));
    }

    @Test
    void getInclusionProofFailsWithDummyItem() throws IOException {
        String toFind = MerkleAccumulator.DUMMY_ADDRRESS;
        List<String> addresses = getAddresses();
        MerkleAccumulator acc = MerkleAccumulator.fromAddresses(addresses.subList(0, 5));
        assertThrows(IllegalArgumentException.class, () -> acc.getInclusionProof(MerkleAccumulator.keyFromAddress(toFind)));
    }

    @Test
    void getInclusionProofVerifiesWithDummyItem() throws IOException {
        String toFind = MerkleAccumulator.DUMMY_ADDRRESS;
        List<String> addresses = getAddresses();
        MerkleAccumulator acc = MerkleAccumulator.fromAddresses(addresses.subList(0, 5));
        byte[] proof = acc.getDummyKeyInclusionProof();
        assertTrue(MerkleAccumulator.verifyInclusionProof(acc.getRootHash(), MerkleAccumulator.keyFromAddress(toFind), proof));
    }
}
