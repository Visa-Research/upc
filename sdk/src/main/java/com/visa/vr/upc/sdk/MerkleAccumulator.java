package com.visa.vr.upc.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

/**
 * A Merkle Accumulator.
 */
public class MerkleAccumulator {

    private final Logger log = LoggerFactory.getLogger(this.getClass());


    private class Node{
        public Node parent;
        public Node leftChild;
        public Node rightChild;
        public byte[] value;
        public boolean isLeft;
    }

    public static final String DUMMY_ADDRRESS = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    private Node root = null;
    private HashMap<String, Node> hashMap;
    private long numKeys;

    /**
     * Constructs a MerkleAccumulator from a list of addresses.
     * @param addresses
     * @return
     */
    public static MerkleAccumulator fromAddresses(List<String> addresses){
        ArrayList<byte[]> hashedKeys = new ArrayList<>(addresses.size());
        for (String address: addresses) {
            if(Numeric.cleanHexPrefix(address.toLowerCase()).length() == 40) {
                hashedKeys.add(keyFromAddress(address));
            }
            else{
                throw new IllegalArgumentException("Address invalid");
            }
        }
        return new MerkleAccumulator(hashedKeys);
    }

    /**
     * Creates a Merkle Accumulator from a list of hashes.
     * @param hashedKeys
     */
    public MerkleAccumulator(List<byte[]> hashedKeys){
        numKeys = hashedKeys.size();

        hashMap = new HashMap<>();

        if(hashedKeys.isEmpty()){
            root = new Node();
            root.value = keyFromAddress(DUMMY_ADDRRESS);
            return;
        }

        int logNumKeys = Integer.SIZE - Integer.numberOfLeadingZeros(hashedKeys.size() - 1);
        hashedKeys.addAll(Collections.nCopies((int) (Math.pow(2, logNumKeys) - hashedKeys.size()), keyFromAddress(DUMMY_ADDRRESS)));
        assert hashedKeys.size() == (int)Math.pow(2, logNumKeys);
        Collections.sort(hashedKeys, (a, b) -> compareByteArray(a, b));

        ArrayList<Node> currentLevel = new ArrayList<Node>();
        for (byte[] hashedKey: hashedKeys) {
            Node node = new Node();
            node.value = hashedKey;
            hashMap.put(Numeric.toHexString(hashedKey), node);
            currentLevel.add(node);
        }
        ArrayList<Node> nextLevel = new ArrayList<>();

        for(int j = 0; j < logNumKeys; j++){
            for(int i = 0; i < currentLevel.size(); i += 2){
                Node leftChild = currentLevel.get(i);
                Node rightChild = currentLevel.get(i + 1);
                nextLevel.add(createLinkedParent(leftChild, rightChild));
            }

            currentLevel = nextLevel;
            nextLevel = new ArrayList<>();
        }

        assert currentLevel.size() == 1;
        root = currentLevel.get(0);
    }

    /**
     * Returns the root hash.
     * @return
     */
    public byte[] getRootHash(){
        return root.value;
    }

    /**
     * Returns the number of keys
     * @return
     */
    public long getNumKeys() { return numKeys; }

    private Node createLinkedParent(Node leftChild, Node rightChild){
        assert leftChild.value != null;
        assert rightChild.value != null;
        Node parent = new Node();
        parent.leftChild = leftChild;
        leftChild.isLeft = true;
        parent.rightChild = rightChild;
        rightChild.isLeft = false;
        leftChild.parent = parent;
        rightChild.parent = parent;
        if(compareByteArray(leftChild.value, rightChild.value) <= 0){
            parent.value = hash(leftChild.value, rightChild.value);
        }
        else{
            parent.value = hash(rightChild.value, leftChild.value);
        }
        return parent;
    }

    /**
     * Hashes a left and right node together.
     * @param left
     * @param right
     * @return
     */
    public static byte[] hash(byte[] left, byte[] right){
        String abiEncoded = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(left),
                new org.web3j.abi.datatypes.generated.Bytes32(right)));
        return Numeric.hexStringToByteArray(Hash.sha3(abiEncoded));
    }

    /**
     * Hashes an address.
     * @param address
     * @return
     */
    public static byte[] keyFromAddress(String address){
        String abiEncoded = FunctionEncoder.encodeConstructor(Arrays.asList(
                new org.web3j.abi.datatypes.Address(address)));
        return Numeric.hexStringToByteArray(Hash.sha3(abiEncoded));
    }

    public static int compareByteArray(byte[] left, byte[] right){
        assert(left.length == right.length);

        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) {
                return Integer.compare(Byte.toUnsignedInt(left[i]), Byte.toUnsignedInt(right[i]));
            }
        }
        return 0;
    }

    /**
     * Returns an inclusion proof of the following format -
     * The proof goes from the leaf to the root.
     * @param hashedKey
     * @return
     * @throws IOException
     */
    public byte[] getInclusionProof(byte[] hashedKey) throws IOException {
        if(Arrays.equals(hashedKey, keyFromAddress(DUMMY_ADDRRESS))){
            throw new IllegalArgumentException("Trying to find proof for dummy key - possible error");
        }
        return unsafeGetInclusionProof(hashedKey);
    }

    public byte[] getDummyKeyInclusionProof() throws IOException {
        return unsafeGetInclusionProof(keyFromAddress(DUMMY_ADDRRESS));
    }

    private byte[] unsafeGetInclusionProof(byte[] hashedKey) throws IOException {
        Node currentNode = hashMap.get(Numeric.toHexString(hashedKey));

        if(currentNode == null){
            throw new NoSuchElementException("Key not present in accumulator");
        }
        ByteArrayOutputStream siblingHashes = new ByteArrayOutputStream();

        byte[] sibling;
        while(!Arrays.equals(currentNode.value, root.value)){
            if(currentNode.isLeft){
                currentNode = currentNode.parent;
                sibling = currentNode.rightChild.value;
            }
            else{
                currentNode = currentNode.parent;
                sibling = currentNode.leftChild.value;
            }
            siblingHashes.write(sibling);
        }
        return siblingHashes.toByteArray();
    }

    /**
     * Verifies an inclusion proof.
     * @param root
     * @param hash
     * @param proof
     * @return
     */
    public static boolean verifyInclusionProof(byte[] root, byte[] hash, byte[] proof){
        int index = 0;
        for(int i = 0; i < proof.length; i += 32){
            byte[] other = Arrays.copyOfRange(proof, i, i + 32);
            if(compareByteArray(hash, other) <= 0){
                hash = MerkleAccumulator.hash(hash, other);
            }
            else{
                hash = MerkleAccumulator.hash(other, hash);
            }
            index += 1;
        }

        return Arrays.equals(root, hash);
    }
}
