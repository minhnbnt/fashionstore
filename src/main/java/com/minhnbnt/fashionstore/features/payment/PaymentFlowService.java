package com.minhnbnt.fashionstore.features.payment;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.payment.PaymentTransactionResponse;
import com.minhnbnt.fashionstore.dto.payment.VietQrSessionResponse;
import com.minhnbnt.fashionstore.features.identity.IdentityDirectoryService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.settings.ConfigAuditService;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import com.minhnbnt.fashionstore.model.PaymentTransaction;
import com.minhnbnt.fashionstore.model.PaymentTransactionStatus;
import com.minhnbnt.fashionstore.model.RefundStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFlowService {

    private final PaymentTransactionService paymentTransactions;

    private final OrderService orderService;

    private final IdentityDirectoryService identityDirectory;

    private final ConfigAuditService configAudit;

    private final Clock clock;

    @Transactional(readOnly = true)
    public VietQrSessionResponse getSessionForViewer(UUID viewerId, boolean isAdmin, String txnRef) {
        return paymentTransactions.getSessionForViewer(txnRef, viewerId, isAdmin);
    }

    @Transactional
    public TransferProcessResult processIncomingTransfer(
        String transferContent,
        long transferAmount,
        @Nullable String bankTransactionId,
        String rawPayload
    ) {
        if (transferAmount <= 0L) {
            return new TransferProcessResult(TransferOutcome.IGNORED, null, 0L, transferAmount, List.of());
        }

        var txnRef = VietQrUrlBuilder.extractTxnRef(transferContent);
        if (txnRef == null) {
            return new TransferProcessResult(TransferOutcome.NOT_FOUND, null, 0L, transferAmount, List.of());
        }

        var txUnlocked = paymentTransactions.findByTxnRef(txnRef);
        if (txUnlocked.isEmpty()) {
            return new TransferProcessResult(TransferOutcome.NOT_FOUND, txnRef, 0L, transferAmount, List.of());
        }

        var txId = txUnlocked.get().getId();
        var orderIds = paymentTransactions.findOrderIdsByTransactionId(txId);
        orderService.lockOrdersInOrder(orderIds);

        var tx = paymentTransactions.findLockedById(txId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));

        if (tx.getStatus() == PaymentTransactionStatus.SUCCESS
            || (bankTransactionId != null
                && !bankTransactionId.isBlank()
                && bankTransactionId.equals(tx.getBankTransactionId()))) {
            return new TransferProcessResult(
                TransferOutcome.ALREADY_PROCESSED,
                tx.getTxnRef(),
                tx.getAmount(),
                tx.getReceivedAmount(),
                orderIds
            );
        }

        var now = clock.instant();
        long effectiveHeld = tx.getReceivedAmount() + transferAmount - tx.alreadyRefundedAmount();
        if (effectiveHeld < tx.getAmount()) {
            tx.markUnderpaid(transferAmount, bankTransactionId, transferContent, rawPayload, now);
            paymentTransactions.save(tx);
            return new TransferProcessResult(
                TransferOutcome.UNDERPAID,
                tx.getTxnRef(),
                tx.getAmount(),
                tx.getReceivedAmount(),
                orderIds
            );
        }

        tx.markSuccess(transferAmount, bankTransactionId, transferContent, rawPayload, now);
        for (var orderId : orderIds) {
            var outcome = orderService.applyVietQrPaymentSuccess(orderId, tx.getTxnRef());
            if (outcome.refundRequired() && outcome.refundReason() != null) {
                tx.addRefund(outcome.total(), outcome.refundReason(), now);
            }
        }
        paymentTransactions.save(tx);

        return new TransferProcessResult(
            TransferOutcome.SUCCESS,
            tx.getTxnRef(),
            tx.getAmount(),
            tx.getReceivedAmount(),
            orderIds
        );
    }

    @Transactional
    public PaymentTransactionResponse confirmTransferByAdmin(
        UUID adminId,
        UUID transactionId,
        @Nullable Long amount
    ) {
        var tx = paymentTransactions.findById(transactionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));

        if (tx.getStatus() == PaymentTransactionStatus.SUCCESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment transaction is already paid");
        }

        var prevStatus = tx.getStatus().name();
        long effectiveHeld = Math.max(0L, tx.getReceivedAmount() - tx.alreadyRefundedAmount());
        var transferDelta = Math.max(1L, tx.getAmount() - effectiveHeld);
        if (amount != null) {
            if (amount <= 0) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Confirmed transfer amount must be positive"
                );
            }
            transferDelta = amount;
        }

        var bankTxId = "ADM-" + UUID.randomUUID().toString().substring(0, 8);
        processIncomingTransfer(
            "ADMIN_CONFIRM " + tx.getTxnRef(),
            transferDelta,
            bankTxId,
            "{\"adminId\":\"" + adminId + "\"}"
        );

        var updated = paymentTransactions.findById(transactionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));

        configAudit.record(
            ConfigAuditEntity.PAYMENT_TRANSACTION,
            updated.getTxnRef(),
            "CONFIRM_TRANSFER",
            prevStatus,
            updated.getStatus().name() + ": " + updated.getReceivedAmount(),
            adminId
        );

        return toTransactionResponse(updated);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public VietQrSessionResponse retryForOrder(UUID buyerId, UUID orderId) {
        return orderService.retryVietQrPayment(buyerId, orderId);
    }

    public int expireOverdueOrders() {
        var expiredOrders = orderService.findExpiredAwaitingPaymentOrders();
        var count = 0;
        for (var order : expiredOrders) {
            try {
                if (orderService.systemExpireAwaitingPaymentOrder(order.getId())) {
                    count++;
                }
            } catch (Exception ex) {
                log.error(
                    "Failed to expire overdue awaiting-payment order {}: {}",
                    order.getId(),
                    ex.getMessage(),
                    ex
                );
            }
        }
        return count;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentTransactionResponse> searchTransactionsForAdmin(
        @Nullable PaymentTransactionStatus status,
        @Nullable RefundStatus refundStatus,
        @Nullable String q,
        int page,
        int size
    ) {
        var safePage = Math.max(0, page);
        var safeSize = Math.clamp(size, 1, 100);
        var found = paymentTransactions.searchForAdmin(status, refundStatus, q, safePage, safeSize);
        var total = paymentTransactions.countForAdmin(status, refundStatus, q);
        var items = found.stream()
            .map(this::toTransactionResponse)
            .toList();
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    @Transactional
    public PaymentTransactionResponse markTransactionRefunded(
        UUID adminId,
        UUID transactionId,
        String note
    ) {
        if (paymentTransactions.findById(transactionId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found");
        }
        var orderIds = paymentTransactions.findOrderIdsByTransactionId(transactionId);
        orderService.lockOrdersInOrder(orderIds);

        var tx = paymentTransactions.findLockedById(transactionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));

        var trimmedNote = note.trim();
        tx.markRefunded(adminId, trimmedNote, clock.instant());
        var saved = paymentTransactions.save(tx);

        for (var orderId : orderIds) {
            orderService.settleOrderRefund(adminId, orderId, trimmedNote);
        }

        configAudit.record(
            ConfigAuditEntity.PAYMENT_TRANSACTION,
            saved.getTxnRef(),
            "REFUND",
            "PENDING",
            "REFUNDED: " + trimmedNote,
            adminId
        );

        return toTransactionResponse(saved);
    }

    private PaymentTransactionResponse toTransactionResponse(PaymentTransaction tx) {
        var buyerEmail = "";
        var account = identityDirectory.findById(tx.getBuyerId()).orElse(null);
        if (account != null && account.email() != null) {
            buyerEmail = account.email();
        }
        var briefs = paymentTransactions.findOrderBriefsByTransactionId(tx.getId());
        return new PaymentTransactionResponse(
            tx.getId(),
            tx.getBuyerId(),
            buyerEmail,
            tx.getProvider(),
            tx.getTxnRef(),
            tx.getAmount(),
            tx.getReceivedAmount(),
            tx.getStatus().name(),
            tx.getBankCode(),
            tx.getAccountNumber(),
            tx.getBankTransactionId(),
            tx.getTransferContent(),
            tx.getPaidAt(),
            tx.getExpiresAt(),
            tx.getRefundStatus().name(),
            tx.getRefundAmount(),
            tx.getRefundReason(),
            tx.getRefundedAt(),
            tx.getRefundNote(),
            briefs,
            tx.getCreatedAt()
        );
    }
}
