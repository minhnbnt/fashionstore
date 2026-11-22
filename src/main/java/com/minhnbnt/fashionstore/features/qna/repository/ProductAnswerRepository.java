package com.minhnbnt.fashionstore.features.qna.repository;

import com.minhnbnt.fashionstore.model.ProductAnswer;
import com.minhnbnt.fashionstore.model.ProductAnswerRole;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductAnswerRepository extends CrudRepository<ProductAnswer, UUID> {

    List<ProductAnswer> findByQuestionIdOrderByCreatedAtAscIdAsc(UUID questionId);

    List<ProductAnswer> findByQuestionIdInOrderByCreatedAtAscIdAsc(Collection<UUID> questionIds);

    long countByQuestionId(UUID questionId);

    long countByQuestionIdAndAuthorRole(UUID questionId, ProductAnswerRole authorRole);
}
