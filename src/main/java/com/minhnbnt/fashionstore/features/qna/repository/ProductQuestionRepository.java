package com.minhnbnt.fashionstore.features.qna.repository;

import com.minhnbnt.fashionstore.model.ProductQuestion;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductQuestionRepository extends CrudRepository<ProductQuestion, UUID> {

    Optional<ProductQuestion> findByIdAndProductId(UUID id, UUID productId);
}
