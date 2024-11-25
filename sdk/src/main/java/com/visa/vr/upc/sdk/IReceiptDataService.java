package com.visa.vr.upc.sdk;

import com.visa.vr.upc.sdk.domain.Receipt;
import com.visa.vr.upc.sdk.domain.StatefulPromise;

import java.util.List;
import java.util.Optional;

/**
 * An interface that handles receipt CRUD operations.
 */
public interface IReceiptDataService {

    void addOutgoingReceipt(Receipt receipt);

    void addIncomingReceipt(Receipt receipt, Long receiptId);

    Optional<Receipt> getIncomingReceiptById(long channelId, long receiptId);

    Optional<Receipt> getOutgoingReceiptById(long channelId, long receiptId);

    Optional<Receipt> getLatestIncomingReceipt(long channelId);

    Optional<Receipt> getLatestOutgoingReceipt(long channelId);
}
