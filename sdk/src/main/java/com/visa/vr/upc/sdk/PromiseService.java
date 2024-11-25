package com.visa.vr.upc.sdk;

import com.visa.vr.upc.sdk.custody.BasicSigner;
import com.visa.vr.upc.sdk.custody.ISigner;
import com.visa.vr.upc.sdk.domain.IPromiseConstructorParams;
import com.visa.vr.upc.sdk.domain.Promise;
import com.visa.vr.upc.sdk.domain.Receipt;
import com.visa.vr.upc.sdk.domain.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides stateless operations related to promises and receipts.
 */
public class PromiseService {
    private static final Logger log = LoggerFactory.getLogger(PromiseService.class);

    /**
     * Constructs an (unsigned) promise. All parameters should be verified before this method.
     * @param channelId the id of the UPC channel
     * @param sender the sender address
     * @param receiver the receiver address
     * @param receiptId
     * @param bytecode the bytecode of the promise contract, in hex
     * @param params the constructor params of the promise
     * @param salt
     * @return the constructed promise
     */
    public static Promise createPromise(long channelId,
                                        long chainId,
                                        String channelAddress,
                                        String sender,
                                        String receiver,
                                        long receiptId,
                                        String bytecode,
                                        IPromiseConstructorParams params,
                                        byte[] salt){
        Promise promise = new Promise();
        promise.setChannelId(channelId);
        promise.setChainId(chainId);
        promise.setSender(sender);
        promise.setReceiver(receiver);
        promise.setReceiptId(receiptId);
        promise.setAmount(params.getAmount());
        promise.setExpiration(params.getExpiration());
        promise.setSalt(salt);

        promise.setBytecode(PromiseService.combineBytecode(bytecode, params));
        promise.setAddress(PromiseService.getCreate2Address(channelAddress, salt, promise.getBytecode()));

        return promise;
    }

    /**
     * Constructs the address associated with the Create2 op-code. This allows a smart contract to deploy a promise
     * to a pre-determinable address. See <a href="https://eips.ethereum.org/EIPS/eip-1014">eip-1014</a>
     * @param address - the address of the deploying contract
     * @param salt
     * @param bytecode the bytecode (including constructor variables) of the contract to be deployed, in hex
     * @return the address at which the bytecode will be deployed
     */
    public static String getCreate2Address(String address, byte[] salt, String bytecode){
        return ContractUtils.generateCreate2ContractAddress(address, salt, Numeric.hexStringToByteArray(bytecode));
    }

    /**
     * Constructs the full deployable bytecode for a contract by combining the contract's binary with encoded constructor parameters
     * @param bytecode the contract binary
     * @param params the constructor parameters to be used for deployment
     * @return the combined bytecode
     */
    public static String combineBytecode(String bytecode, IPromiseConstructorParams params){
        return bytecode + params.encodePacked();
    }

    /**
     * Signs a promise
     * @param promise the promise to be signed
     * @param signer a signer object, which can either be a simple keypair wrapper or do something more complicated like query an HSM
     * @return the signed promise
     */
    public static Promise signPromise(Promise promise, ISigner signer){
        if(!AddressUtils.isEqual(promise.getSender(), signer.getAddress())){
            throw new IllegalArgumentException("Signer has a different address from sender");
        }
        byte[] hash = PromiseService.hashPromise(promise);
        Sign.SignatureData sigData = signer.signPrefixedMessage(hash);
        promise.setSignature(new Signature(sigData));
        return promise;
    }

    /**
     * See {@link #signPromise(Promise, ISigner)}
     * @param promise
     * @param keyPair
     * @return
     */
    public static Promise signPromise(Promise promise, ECKeyPair keyPair){
        return PromiseService.signPromise(promise, new BasicSigner(keyPair));
    }

    /**
     * ABI encodes and hashes a promise, consistent with UPC contract.
     * @param promise the promise to hash
     * @return the hash
     */
    public static byte[] hashPromise(Promise promise){
        String abiEncoded = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(promise.getChainId()),
                new org.web3j.abi.datatypes.generated.Uint256(promise.getChannelId()),
                new org.web3j.abi.datatypes.generated.Uint256(promise.getReceiptId()),
                new org.web3j.abi.datatypes.Address(promise.getSender()),
                new org.web3j.abi.datatypes.Address(promise.getReceiver()),
                new org.web3j.abi.datatypes.Address(promise.getAddress())));
        return Numeric.hexStringToByteArray(Hash.sha3(abiEncoded));
    }

    /**
     *
     * @param promise
     * @param channelId
     * @param channelAddress
     * @param sender
     * @param receiver
     * @param receiptId
     * @param bytecode
     * @param params
     * @param salt
     * @return
     * @throws SignatureException
     */
    public static boolean verifyPromise(Promise promise,
                                        long channelId,
                                        long chainId,
                                        String channelAddress,
                                        String sender,
                                        String receiver,
                                        long receiptId,
                                        String bytecode,
                                        IPromiseConstructorParams params,
                                        byte[] salt) throws SignatureException {
        Promise expectedPromise = PromiseService.createPromise(channelId, chainId, channelAddress, sender, receiver, receiptId, bytecode, params, salt);
        byte[] expectedHash = PromiseService.hashPromise(expectedPromise);
        if(!Arrays.equals(expectedHash, PromiseService.hashPromise(promise))){
            log.info("Promise hash mismatch on Channel {}", channelId);
            return false;
        }
        if(!AddressUtils.isEqual(sender, Keys.getAddress(Sign.signedPrefixedMessageToKey(expectedHash, promise.getSignature().toSignatureData())))){
            log.info("Promise signature failed to validate on Channel {}", channelId);
            return false;
        }
        return true;
    }

    /**
     *
     * @param channelId
     * @param sender
     * @param receiver
     * @param cumulativeCredit
     * @param promises
     * @return
     */
    public static Receipt createReceipt(long channelId,
                                        long chainId,
                                        String sender,
                                        String receiver,
                                        long receiptId,
                                        long cumulativeCredit,
                                        List<? extends Promise> promises){
        byte[] accumulatorRoot = MerkleAccumulator.fromAddresses(promises.stream().map(p -> p.getAddress()).collect(Collectors.toList())).getRootHash();
        return PromiseService.createReceipt(channelId, chainId, sender, receiver, receiptId, cumulativeCredit, accumulatorRoot);
    };

    /**
     *
     * @param channelId
     * @param sender
     * @param receiver
     * @param cumulativeCredit
     * @param accumulatorRoot
     * @return
     */
    public static Receipt createReceipt(long channelId,
                                        long chainId,
                                        String sender,
                                        String receiver,
                                        long receiptId,
                                        long cumulativeCredit,
                                        byte[] accumulatorRoot){
        Receipt receipt = new Receipt();
        receipt.setChannelId(channelId);
        receipt.setChainId(chainId);
        receipt.setSender(sender);
        receipt.setReceiver(receiver);
        receipt.setReceiptId(receiptId);
        receipt.setCumulativeCredit(cumulativeCredit);
        receipt.setAccumulatorRoot(accumulatorRoot);
        return receipt;
    };

    /**
     *
     * @param receipt
     * @param signer
     * @return
     */
    public static Receipt signReceipt(Receipt receipt, ISigner signer) {
        if(!AddressUtils.isEqual(receipt.getSender(), signer.getAddress())){
            throw new IllegalArgumentException("Signer has a different address from sender");
        }
        byte[] hash = PromiseService.hashReceipt(receipt);
        Sign.SignatureData sigData = signer.signPrefixedMessage(hash);
        receipt.setSignature(new Signature(sigData));
        return receipt;
    }

    /**
     * See {@link #signReceipt(Receipt, ISigner)}
     * @param receipt
     * @param keyPair
     * @return
     */
    public static Receipt signReceipt(Receipt receipt, ECKeyPair keyPair){
        return PromiseService.signReceipt(receipt, new BasicSigner(keyPair));
    }

    /**
     *
     * @param receipt
     * @return
     */
    public static byte[] hashReceipt(Receipt receipt){
        String abiEncoded = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(receipt.getChainId()),
                new org.web3j.abi.datatypes.generated.Uint256(receipt.getChannelId()),
                new org.web3j.abi.datatypes.generated.Uint256(receipt.getReceiptId()),
                new org.web3j.abi.datatypes.generated.Uint256(receipt.getCumulativeCredit()),
                new org.web3j.abi.datatypes.generated.Bytes32(receipt.getAccumulatorRoot())));
        return Numeric.hexStringToByteArray(Hash.sha3(abiEncoded));
    }

    /**
     *
     * @param receipt
     * @param channelId
     * @param sender
     * @param receiver
     * @param cumulativeCredit
     * @param promises
     * @return
     * @throws SignatureException
     */
    public static boolean verifyReceipt(Receipt receipt,
                                        long channelId,
                                        long chainId,
                                        String sender,
                                        String receiver,
                                        long receiptId,
                                        long cumulativeCredit,
                                        List<? extends Promise> promises) throws SignatureException {
        byte[] accumulatorRoot = getAccumulator(promises).getRootHash();
        return PromiseService.verifyReceipt(receipt, channelId, chainId, sender, receiver, receiptId, cumulativeCredit, accumulatorRoot);
    }

    /**
     * Builds a Merkle Accumulator from a list of promises.
     * @param promises
     * @return
     */
    public static MerkleAccumulator getAccumulator(List<? extends Promise> promises){
        return MerkleAccumulator.fromAddresses(promises.stream().map(p -> p.getAddress()).collect(Collectors.toList()));
    }

    /**
     *
     * @param receipt
     * @param channelId
     * @param sender
     * @param receiver
     * @param credit
     * @param accumulatorRoot
     * @return
     * @throws SignatureException
     */
    public static boolean verifyReceipt(Receipt receipt,
                                        Long channelId,
                                        Long chainId,
                                        String sender,
                                        String receiver,
                                        Long receiptId,
                                        Long credit,
                                        byte[] accumulatorRoot) throws SignatureException {
        Receipt expectedReceipt = PromiseService.createReceipt(channelId, chainId, sender, receiver, receiptId, credit, accumulatorRoot);
        byte[] expectedHash = PromiseService.hashReceipt(expectedReceipt);
        if(!Arrays.equals(expectedHash, PromiseService.hashReceipt(receipt))){
            log.info("Receipt hash mismatch on Channel {}", channelId);
            return false;
        }
        if(!AddressUtils.isEqual(sender, Keys.getAddress(Sign.signedPrefixedMessageToKey(expectedHash, receipt.getSignature().toSignatureData())))){
            log.info("Receipt signature failed to validate on Channel {}", channelId);
            return false;
        }
        return true;
    }

    /**
     * Retrieves an accumulator proof from an accumulator and a promise.
     * @param acc
     * @param promise
     * @return
     * @throws IOException
     */
    public static byte[] getAccumulatorProof(MerkleAccumulator acc, Promise promise) throws IOException {
        return acc.getInclusionProof(MerkleAccumulator.keyFromAddress(promise.getAddress()));
    }
}
