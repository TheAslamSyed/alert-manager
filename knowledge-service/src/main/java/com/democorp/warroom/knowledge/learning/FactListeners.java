package com.democorp.warroom.knowledge.learning;

import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FactListeners {

    private final PostmortemService postmortems;
    private final EventCodec codec;

    public FactListeners(PostmortemService postmortems, EventCodec codec) {
        this.postmortems = postmortems;
        this.codec = codec;
    }

    @KafkaListener(id = "knowledge-incidents", topics = Topics.INCIDENT)
    public void incidents(String json) {
        postmortems.onEvent(codec.decode(json), "knowledge.incident");
    }

    @KafkaListener(id = "knowledge-investigations", topics = Topics.INVESTIGATION)
    public void investigations(String json) {
        postmortems.onEvent(codec.decode(json), "knowledge.investigation");
    }

    @KafkaListener(id = "knowledge-remediations", topics = Topics.REMEDIATION)
    public void remediations(String json) {
        postmortems.onEvent(codec.decode(json), "knowledge.remediation");
    }
}
