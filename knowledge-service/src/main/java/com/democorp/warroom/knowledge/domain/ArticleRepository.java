package com.democorp.warroom.knowledge.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, String> {

    List<Article> findByStatusAndIndexedFalse(String status);

    List<Article> findByKindOrderByCreatedAtDesc(String kind);

    List<Article> findByStatusOrderByCreatedAtDesc(String status);

    List<Article> findAllByOrderByCreatedAtDesc();
}
