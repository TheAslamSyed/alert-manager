package com.democorp.warroom.agent.tools;

import com.democorp.warroom.agent.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only LogQL tool. Log lines are logfmt, so fields are parsed into a map. */
@Component
public class LokiTool {

    private static final Pattern KV = Pattern.compile("(\\w+)=(\"[^\"]*\"|\\S+)");

    private final RestClient http;
    private final AgentProperties props;

    public LokiTool(RestClient toolClient, AgentProperties props) {
        this.http = toolClient;
        this.props = props;
    }

    public List<LogLine> query(String logql, Instant from, Instant to, int limit) {
        URI uri = UriComponentsBuilder.fromUriString(props.lokiUrl()).path("/loki/api/v1/query_range")
                .queryParam("query", "{q}")
                .queryParam("start", from.toEpochMilli() * 1_000_000L)
                .queryParam("end", to.toEpochMilli() * 1_000_000L)
                .queryParam("limit", limit)
                .queryParam("direction", "forward")
                .buildAndExpand(logql).encode().toUri();
        JsonNode body = http.get().uri(uri).retrieve().body(JsonNode.class);
        List<LogLine> lines = new ArrayList<>();
        if (body == null) {
            return lines;
        }
        for (JsonNode stream : body.path("data").path("result")) {
            for (JsonNode v : stream.path("values")) {
                long nanos = Long.parseLong(v.path(0).asText("0"));
                String line = v.path(1).asText("");
                lines.add(new LogLine(Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L), line,
                        fields(line)));
            }
        }
        lines.sort(Comparator.comparing(LogLine::at));
        return lines;
    }

    static Map<String, String> fields(String line) {
        Map<String, String> m = new HashMap<>();
        Matcher matcher = KV.matcher(line);
        while (matcher.find()) {
            String value = matcher.group(2);
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            m.putIfAbsent(matcher.group(1), value);
        }
        return m;
    }

    public record LogLine(Instant at, String raw, Map<String, String> fields) {
        public String field(String name) {
            return fields.getOrDefault(name, "");
        }
    }
}
