package com.democorp.warroom.knowledge.search;

import com.democorp.warroom.knowledge.domain.Article;
import com.democorp.warroom.knowledge.domain.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FR-05: hybrid search. Meaning-based (pgvector embeddings) and keyword (Postgres full-text)
 * results are merged with Reciprocal Rank Fusion. If the embedding model is down,
 * keyword search still answers (degraded = true).
 */
@Service
public class KnowledgeSearch {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearch.class);
    private static final int RRF_K = 60;
    private static final int CANDIDATES = 12;

    private final VectorStore vectors;
    private final JdbcTemplate jdbc;
    private final ArticleRepository articles;

    public KnowledgeSearch(VectorStore vectors, JdbcTemplate jdbc, ArticleRepository articles) {
        this.vectors = vectors;
        this.jdbc = jdbc;
        this.articles = articles;
    }

    public SearchResponse search(String query, int k) {
        Map<String, Double> similarity = new HashMap<>();
        List<String> vectorRanked = new ArrayList<>();
        boolean degraded = false;
        try {
            List<Document> docs = vectors.similaritySearch(SearchRequest.builder().query(query).topK(CANDIDATES).build());
            for (Document d : docs) {
                String articleId = String.valueOf(d.getMetadata().get("articleId"));
                if (!vectorRanked.contains(articleId)) {
                    vectorRanked.add(articleId);
                    similarity.put(articleId, d.getScore() == null ? 0.0 : d.getScore());
                }
            }
        } catch (RuntimeException e) {
            degraded = true;
            log.warn("Vector search unavailable, using keyword search only: {}", e.getMessage());
        }

        List<String> keywordRanked = jdbc.queryForList(
                "SELECT id FROM kb_article WHERE status = 'PUBLISHED' AND search @@ websearch_to_tsquery('english', ?) "
                        + "ORDER BY ts_rank(search, websearch_to_tsquery('english', ?)) DESC LIMIT " + CANDIDATES,
                String.class, orQuery(query), orQuery(query));

        Map<String, Double> fused = new LinkedHashMap<>();
        addRanks(fused, vectorRanked);
        addRanks(fused, keywordRanked);

        List<Hit> hits = fused.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(Math.max(1, k))
                .map(e -> articles.findById(e.getKey())
                        .filter(a -> Article.PUBLISHED.equals(a.getStatus()))
                        .map(a -> Hit.of(a, similarity.getOrDefault(a.getId(), 0.0), e.getValue(),
                                vectorRanked.contains(a.getId()), keywordRanked.contains(a.getId())))
                        .orElse(null))
                .filter(h -> h != null)
                .toList();
        return new SearchResponse(query, degraded, hits);
    }

    private static void addRanks(Map<String, Double> fused, List<String> ranked) {
        for (int i = 0; i < ranked.size(); i++) {
            fused.merge(ranked.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }

    /** "pool exhausted after release" -> "pool or exhausted or release" (any word may match). */
    static String orQuery(String query) {
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9.]+"))
                .filter(w -> w.length() > 2)
                .distinct()
                .collect(Collectors.joining(" or "));
    }

    public record SearchResponse(String query, boolean degraded, List<Hit> hits) {
    }

    public record Hit(String articleId, String ref, String kind, String title, String service, String summary,
                      String rootCause, String resolutionAction, String resolution, Integer mttrMinutes,
                      double similarity, double score, String matchedBy) {
        static Hit of(Article a, double similarity, double score, boolean byVector, boolean byKeyword) {
            String matched = byVector && byKeyword ? "meaning+keywords" : byVector ? "meaning" : "keywords";
            return new Hit(a.getId(), a.getRef(), a.getKind(), a.getTitle(), a.getService(), a.getSummary(),
                    a.getRootCause(), a.getResolutionAction(), a.getResolution(), a.getMttrMinutes(),
                    Math.round(similarity * 1000) / 1000.0, score, matched);
        }
    }
}
