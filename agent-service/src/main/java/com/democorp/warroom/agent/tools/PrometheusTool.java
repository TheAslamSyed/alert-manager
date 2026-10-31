package com.democorp.warroom.agent.tools;

import com.democorp.warroom.agent.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Read-only PromQL tool (instant queries). */
@Component
public class PrometheusTool {

    private final RestClient http;
    private final AgentProperties props;

    public PrometheusTool(RestClient toolClient, AgentProperties props) {
        this.http = toolClient;
        this.props = props;
    }

    public List<Sample> query(String promql) {
        URI uri = UriComponentsBuilder.fromUriString(props.prometheusUrl()).path("/api/v1/query")
                .queryParam("query", "{q}").buildAndExpand(promql).encode().toUri();
        JsonNode body = http.get().uri(uri).retrieve().body(JsonNode.class);
        List<Sample> samples = new ArrayList<>();
        if (body == null || !"success".equals(body.path("status").asText())) {
            return samples;
        }
        for (JsonNode r : body.path("data").path("result")) {
            Map<String, String> labels = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = r.path("metric").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                labels.put(e.getKey(), e.getValue().asText());
            }
            String raw = r.path("value").path(1).asText("NaN");
            double value;
            try {
                value = Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                value = Double.NaN;
            }
            samples.add(new Sample(labels, value));
        }
        return samples;
    }

    public record Sample(Map<String, String> labels, double value) {
        public String label(String name) {
            return labels.getOrDefault(name, "");
        }

        public boolean valid() {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }
}
