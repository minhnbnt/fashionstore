package com.minhnbnt.fashionstore.features.payment.repository;

import com.minhnbnt.fashionstore.model.PaymentGateway;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentGatewayRepository extends ListCrudRepository<PaymentGateway, String> {

    @Query("SELECT * FROM payment_gateway ORDER BY code ASC")
    List<PaymentGateway> findAllOrdered();

    @Query("SELECT * FROM payment_gateway ORDER BY code ASC FOR UPDATE")
    List<PaymentGateway> findAllForUpdate();

    @Query("SELECT * FROM payment_gateway WHERE code = :code")
    Optional<PaymentGateway> findByCode(String code);
}
