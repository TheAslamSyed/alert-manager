package com.democorp.warroom.agent.tools;

import com.democorp.warroom.agent.AgentProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Read-only knowledge search tool (calls knowledge-service inside the Docker network). */
@Component
public class KnowledgeTool {

    private final RestClient http;
    private final AgentProperties props;

    public KnowledgeTool(RestClient toolClient, AgentProperties props) {
        this.http = toolClient;
        this.props = props;
    }

    public Result search(String query, int k) {
        Result r = http.post().uri(props.knowledgeUrl() + "/internal/search")
                .body(Map.of("query", query, "k", k))
                .retrieve().body(Result.class);
        return r == null ? new Result(query, true, List.of()) : r;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String query, boolean degraded, List<Hit> hits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(String ref, String kind, String title, String service, String rootCause, String resolutionAction,
                      String resolution, Integer mttrMinutes, double similarity, double score, String matchedBy) {
    }
}
