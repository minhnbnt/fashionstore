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

    private final PaymentGatewayService paymentGateways;

    private final IdentityDirectoryService identityDirectory;

    private final ConfigAuditService configAudit;

    private final Clock clock;

    @Transactional(readOnly = true)
    public VietQrSessionResponse getSessionForViewer(UUID viewerId, boolean isAdmin, String txnRef) {
        return paymentTransactions.getSessionForViewer(txnRef, viewerId, isAdmin);
    }

    @Transactional(readOnly = true)
    public VietQrSessionResponse getSessionForViewer(String txnRef, UUID viewerId, boolean isAdmin) {
        return paymentTransactions.getSessionForViewer(txnRef, viewerId, isAdmin);
    }

    @Transactional
    public TransferProcessResult processIncomingTransfer(
        String transferContent,
        long transferAmount,
        @Nullable String bankTransactionId,
        String rawPayload
    ) {
        var txnRef = VietQrUrlBuilder.extractTxnRef(transferContent);
        if (txnRef == null) {
            return new TransferProcessResult(TransferOutcome.NOT_FOUND, null, 0L, transferAmount, List.of());
        }

        var txOpt = paymentTransactions.findLockedByTxnRef(txnRef);
        if (txOpt.isEmpty()) {
            return new TransferProcessResult(TransferOutcome.NOT_FOUND, txnRef, 0L, transferAmount, List.of());
        }

        var tx = txOpt.get();
        var orderIds = paymentTransactions.findOrderIdsByTransactionId(tx.getId());

        if (tx.getStatus() == PaymentTransactionStatus.SUCCESS) {
            return new TransferProcessResult(
                TransferOutcome.ALREADY_PROCESSED,
                tx.getTxnRef(),
                tx.getAmount(),
                tx.getReceivedAmount(),
                orderIds
            );
        }

        var now = clock.instant();
        if (transferAmount < tx.getAmount()) {
            tx.markUnderpaid(transferAmount, bankTransactionId, transferContent, rawPayload, now);
            paymentTransactions.save(tx);
            return new TransferProcessResult(
                TransferOutcome.UNDERPAID,
                tx.getTxnRef(),
                tx.getAmount(),
                transferAmount,
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
            transferAmount,
            orderIds
        );
    }

    @Transactional
    public VietQrSessionResponse simulateTransfer(
        UUID callerId,
        boolean isAdmin,
        String txnRef,
        @Nullable Long amount
    ) {
        var gateway = paymentGateways.requireActiveVietQrGateway();
        if (!gateway.isSandboxMode()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VietQR sandbox simulation is disabled");
        }

        var tx = paymentTransactions.findByTxnRef(txnRef)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));

        if (!isAdmin && !tx.getBuyerId().equals(callerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found");
        }

        var simulatedAmount = tx.getAmount();
        if (amount != null) {
            if (amount <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulated transfer amount must be positive");
            }
            simulatedAmount = amount;
        }

        var bankTxId = "SBX-" + UUID.randomUUID().toString().substring(0, 8);
        processIncomingTransfer("SANDBOX " + txnRef, simulatedAmount, bankTxId, "{\"sandbox\":true}");
        return getSessionForViewer(callerId, isAdmin, txnRef);
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
        var tx = paymentTransactions.findLockedById(transactionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));

        var trimmedNote = note.trim();
        tx.markRefunded(adminId, trimmedNote, clock.instant());
        var saved = paymentTransactions.save(tx);

        var orderIds = paymentTransactions.findOrderIdsByTransactionId(saved.getId());
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
