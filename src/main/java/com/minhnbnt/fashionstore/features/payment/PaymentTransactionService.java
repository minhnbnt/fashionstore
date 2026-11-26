package com.minhnbnt.fashionstore.features.payment;

import com.minhnbnt.fashionstore.dto.payment.PaymentGatewayResponse;
import com.minhnbnt.fashionstore.dto.payment.VietQrSessionResponse;
import com.minhnbnt.fashionstore.features.payment.repository.PaymentTransactionRepository;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import com.minhnbnt.fashionstore.model.PaymentTransaction;
import com.minhnbnt.fashionstore.model.PaymentTransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentTransactionRepository transactions;

    private final PaymentGatewayService paymentGateways;

    @Transactional
    public PaymentTransaction createSession(UUID buyerId, List<Order> orders, Instant expiresAt) {
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Orders list must not be empty");
        }

        var gateway = paymentGateways.requireActiveVietQrGateway();
        var bankBin = Objects.requireNonNull(gateway.getBankBin());
        var accountNumber = Objects.requireNonNull(gateway.getAccountNumber());
        var accountName = Objects.requireNonNull(gateway.getAccountName());

        var bankCode = gateway.getBankCode();
        if (bankCode == null || bankCode.isBlank()) {
            bankCode = bankBin;
        }

        var totalAmount = 0L;
        var orderIds = new ArrayList<UUID>(orders.size());
        for (var order : orders) {
            totalAmount += order.getTotal();
            orderIds.add(order.getId());
        }

        var txnRef = generateUniqueTxnRef();
        var qrImageUrl = VietQrUrlBuilder.buildQrImageUrl(
            bankBin,
            accountNumber,
            accountName,
            totalAmount,
            txnRef
        );

        var tx = PaymentTransaction.create(
            buyerId,
            txnRef,
            totalAmount,
            bankBin,
            bankCode,
            accountNumber,
            accountName,
            qrImageUrl,
            expiresAt,
            Instant.now()
        );
        var saved = transactions.save(tx);
        transactions.linkOrders(saved.getId(), orderIds);
        return saved;
    }

    @Transactional
    public PaymentTransaction createVietQrSession(UUID buyerId, List<Order> orders, Instant expiresAt) {
        return createSession(buyerId, orders, expiresAt);
    }

    @Transactional(readOnly = true)
    public List<UUID> findOrderIdsByTransactionId(UUID transactionId) {
        return transactions.findOrderIdsByTransactionId(transactionId);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findByTxnRef(String txnRef) {
        return transactions.findByTxnRef(txnRef);
    }

    @Transactional
    public Optional<PaymentTransaction> findLockedByTxnRef(String txnRef) {
        return transactions.findLockedByTxnRef(txnRef);
    }

    @Transactional
    public PaymentTransaction save(PaymentTransaction tx) {
        return transactions.save(tx);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findPendingSessionForOrder(UUID orderId, Instant now) {
        var list = transactions.findTransactionsByOrderId(orderId);
        for (var tx : list) {
            if (tx.getStatus() == PaymentTransactionStatus.PENDING && tx.getExpiresAt().isAfter(now)) {
                return Optional.of(tx);
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void expirePendingSessionsForOrder(UUID orderId, Instant now) {
        var list = transactions.findTransactionsByOrderId(orderId);
        for (var tx : list) {
            if (tx.getStatus() == PaymentTransactionStatus.PENDING) {
                var locked = transactions.findLockedById(tx.getId()).orElse(null);
                if (locked != null && locked.getStatus() == PaymentTransactionStatus.PENDING) {
                    locked.markExpired(now);
                    transactions.save(locked);
                }
            }
        }
    }

    @Transactional
    public void flagOrderRefund(UUID orderId, long refundAmount, String reason) {
        var list = transactions.findTransactionsByOrderId(orderId);
        for (var tx : list) {
            if (tx.getStatus() == PaymentTransactionStatus.SUCCESS) {
                var locked = transactions.findLockedById(tx.getId()).orElse(null);
                if (locked != null) {
                    locked.addRefund(refundAmount, reason, Instant.now());
                    transactions.save(locked);
                    return;
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public VietQrSessionResponse getSessionForViewer(String txnRef, UUID viewerId, boolean isAdmin) {
        var tx = transactions.findByTxnRef(txnRef)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));

        if (!isAdmin && !tx.getBuyerId().equals(viewerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found");
        }

        return toSessionResponse(tx);
    }

    @Transactional(readOnly = true)
    public VietQrSessionResponse getSessionForViewer(UUID viewerId, boolean isAdmin, String txnRef) {
        return getSessionForViewer(txnRef, viewerId, isAdmin);
    }

    @Transactional(readOnly = true)
    public VietQrSessionResponse getSessionForViewer(String txnRef, UUID viewerId) {
        return getSessionForViewer(txnRef, viewerId, false);
    }

    @Transactional(readOnly = true)
    public VietQrSessionResponse getSessionForViewer(UUID viewerId, String txnRef) {
        return getSessionForViewer(txnRef, viewerId, false);
    }

    @Transactional(readOnly = true)
    public VietQrSessionResponse toSessionResponse(PaymentTransaction tx) {
        var briefs = transactions.findOrderBriefsByTransactionId(tx.getId());
        var vietQrConfig = findVietQrGatewayResponse();

        var bankName = tx.getBankCode();
        var sandboxMode = vietQrConfig.sandboxMode();
        var configuredBankName = vietQrConfig.bankName();
        if (configuredBankName != null && !configuredBankName.isBlank()) {
            bankName = configuredBankName;
        }

        return new VietQrSessionResponse(
            tx.getId(),
            tx.getTxnRef(),
            tx.getStatus().name(),
            tx.getAmount(),
            tx.getReceivedAmount(),
            tx.getBankBin(),
            tx.getBankCode(),
            bankName,
            tx.getAccountNumber(),
            tx.getAccountName(),
            tx.getQrImageUrl(),
            tx.getExpiresAt(),
            tx.getPaidAt(),
            sandboxMode,
            tx.getRefundStatus().name(),
            tx.getRefundAmount(),
            tx.getRefundReason(),
            briefs
        );
    }

    private PaymentGatewayResponse findVietQrGatewayResponse() {
        for (var g : paymentGateways.list()) {
            if (PaymentMethod.VIETQR.name().equals(g.code())) {
                return g;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "VietQR gateway configuration missing");
    }

    private String generateUniqueTxnRef() {
        for (int attempt = 0; attempt < 5; attempt++) {
            var candidate = VietQrUrlBuilder.generateTxnRef();
            if (transactions.findByTxnRef(candidate).isEmpty()) {
                return candidate;
            }
        }
        return VietQrUrlBuilder.generateTxnRef();
    }
}
