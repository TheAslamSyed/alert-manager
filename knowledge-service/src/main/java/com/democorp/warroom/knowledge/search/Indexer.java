package com.democorp.warroom.knowledge.search;

import com.democorp.warroom.knowledge.domain.Article;
import com.democorp.warroom.knowledge.domain.ArticleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds DemoCorp's history on first start, then keeps embeddings in sync: every PUBLISHED article
 * that is not yet indexed gets embedded. If Ollama is down it simply retries on the next run.
 */
@Component
public class Indexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Indexer.class);

    private final ArticleRepository articles;
    private final VectorStore vectors;
    private final ObjectMapper mapper;

    public Indexer(ArticleRepository articles, VectorStore vectors, ObjectMapper mapper) {
        this.articles = articles;
        this.vectors = vectors;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (articles.count() > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource("seed/knowledge-seed.json").getInputStream()) {
            List<Map<String, Object>> seed = mapper.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    new TypeReference<>() { });
            for (Map<String, Object> s : seed) {
                String ref = (String) s.get("ref");
                Integer days = (Integer) s.get("daysAgo");
                articles.save(new Article("KB-" + ref, (String) s.get("kind"), ref, (String) s.get("title"),
                        (String) s.get("service"), (String) s.get("summary"), (String) s.get("rootCause"),
                        (String) s.get("action"), (String) s.get("resolution"), (Integer) s.get("mttr"), null,
                        Article.PUBLISHED, days == null ? null : Instant.now().minus(days, ChronoUnit.DAYS)));
            }
            log.info("Seeded {} knowledge articles", seed.size());
        }
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 30_000)
    @Transactional
    public void indexPending() {
        List<Article> pending = articles.findByStatusAndIndexedFalse(Article.PUBLISHED);
        if (pending.isEmpty()) {
            return;
        }
        try {
            List<Document> docs = pending.stream().map(a -> new Document(
                    UUID.nameUUIDFromBytes(a.getId().getBytes(StandardCharsets.UTF_8)).toString(),
                    a.embeddingText(),
                    Map.<String, Object>of("articleId", a.getId(), "ref", a.getRef(), "kind", a.getKind(),
                            "service", a.getService() == null ? "" : a.getService()))).toList();
            vectors.add(docs);
            pending.forEach(Article::markIndexed);
            articles.saveAll(pending);
            log.info("Indexed {} knowledge articles", pending.size());
        } catch (RuntimeException e) {
            log.warn("Indexing deferred ({} articles waiting): {}", pending.size(), e.getMessage());
        }
    }
}
