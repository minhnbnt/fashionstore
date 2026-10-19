package com.minhnbnt.fashionstore.features.content.repository;

import com.minhnbnt.fashionstore.model.SitePage;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SitePageRepository extends CrudRepository<SitePage, UUID> {

    List<SitePage> findAllByOrderByUpdatedAtDesc();

    @Query("SELECT * FROM site_page WHERE status = 'PUBLISHED' ORDER BY sort_order, title")
    List<SitePage> findPublished();

    @Query("SELECT * FROM site_page WHERE slug = :slug AND status = 'PUBLISHED'")
    Optional<SitePage> findPublishedBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT EXISTS (SELECT 1 FROM site_page WHERE slug = :slug AND id <> :id)")
    boolean existsBySlugOnOtherPage(String slug, UUID id);
}
