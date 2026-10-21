package com.minhnbnt.fashionstore.features.catalog.repository;

import com.minhnbnt.fashionstore.model.BannedKeyword;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BannedKeywordRepository extends CrudRepository<BannedKeyword, UUID> {

    @Query("SELECT * FROM banned_keyword ORDER BY created_at, id")
    List<BannedKeyword> findAllOldestFirst();

    @Query("SELECT EXISTS (SELECT 1 FROM banned_keyword WHERE lower(keyword) = lower(:keyword))")
    boolean existsByKeywordIgnoringCase(@Param("keyword") String keyword);
}
