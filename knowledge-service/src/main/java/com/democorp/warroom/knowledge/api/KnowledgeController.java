package com.democorp.warroom.knowledge.api;

import com.democorp.warroom.knowledge.domain.Article;
import com.democorp.warroom.knowledge.domain.ArticleRepository;
import com.democorp.warroom.knowledge.search.KnowledgeSearch;
import com.democorp.warroom.knowledge.search.KnowledgeSearch.SearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@Tag(name = "Knowledge", description = "Past incidents, runbooks and postmortems (FR-05, FR-10)")
public class KnowledgeController {

    private final KnowledgeSearch search;
    private final ArticleRepository articles;

    public KnowledgeController(KnowledgeSearch search, ArticleRepository articles) {
        this.search = search;
        this.articles = articles;
    }

    /** Used by the RCA agent inside the Docker network. */
    @PostMapping("/internal/search")
    public SearchResponse internalSearch(@Valid @RequestBody SearchRequest request) {
        return search.search(request.query(), request.k() == null ? 5 : request.k());
    }

    @GetMapping("/api/knowledge/search")
    @Operation(summary = "Hybrid search (meaning + keywords) over published articles")
    public SearchResponse search(@RequestParam String q, @RequestParam(defaultValue = "5") int k) {
        return search.search(q, k);
    }

    @GetMapping("/api/knowledge/articles")
    public List<Article> list(@RequestParam(required = false) String kind, @RequestParam(required = false) String status) {
        if (kind != null) {
            return articles.findByKindOrderByCreatedAtDesc(kind);
        }
        if (status != null) {
            return articles.findByStatusOrderByCreatedAtDesc(status);
        }
        return articles.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/api/knowledge/articles/{id}")
    public Article get(@PathVariable String id) {
        return find(id);
    }

    @PutMapping("/api/knowledge/articles/{id}")
    @PreAuthorize("hasAnyRole('RESPONDER', 'APPROVER')")
    @Transactional
    public Article edit(@PathVariable String id, @RequestBody ArticleEdit edit) {
        Article a = find(id);
        a.edit(edit.summary(), edit.rootCause(), edit.resolution(), edit.body());
        return articles.save(a);
    }

    @PostMapping("/api/knowledge/articles/{id}/publish")
    @PreAuthorize("hasAnyRole('RESPONDER', 'APPROVER')")
    @Operation(summary = "Review and publish a postmortem; it becomes searchable within 30 s")
    @Transactional
    public Article publish(@PathVariable String id, Authentication user) {
        Article a = find(id);
        a.publish(user.getName());
        return articles.save(a);
    }

    private Article find(String id) {
        return articles.findById(id).orElseThrow(() -> new NoSuchElementException("No article " + id));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    public record SearchRequest(@NotBlank String query, Integer k) {
    }

    public record ArticleEdit(String summary, String rootCause, String resolution, String body) {
    }
}
