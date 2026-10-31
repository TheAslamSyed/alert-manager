package com.democorp.warroom.common.autoconfigure;

import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.EventPublisher;
import com.democorp.warroom.common.events.Topics;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared Kafka setup: creates all topics (plus dead-letter topics), retries failed messages
 * twice, then parks them on {@code <topic>.DLT} so one bad message never blocks a partition.
 */
@AutoConfiguration(after = {KafkaAutoConfiguration.class, WarroomCoreAutoConfiguration.class})
@ConditionalOnClass(KafkaTemplate.class)
public class WarroomKafkaAutoConfiguration {

    @Bean
    KafkaAdmin.NewTopics warroomTopics(@Value("${warroom.kafka.partitions:3}") int partitions) {
        List<NewTopic> topics = new ArrayList<>();
        for (String topic : Topics.ALL) {
            topics.add(TopicBuilder.name(topic).partitions(partitions).replicas(1).build());
            topics.add(TopicBuilder.name(Topics.deadLetter(topic)).partitions(partitions).replicas(1).build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler warroomKafkaErrorHandler(KafkaTemplate<String, String> template) {
        // Default destination: same topic name + ".DLT", same partition.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(EventCodec.MalformedEventException.class);
        return handler;
    }

    @Bean
    @ConditionalOnMissingBean
    EventPublisher eventPublisher(KafkaTemplate<String, String> kafka, EventCodec codec,
                                  @Value("${spring.application.name}") String producer,
                                  ObjectProvider<Tracer> tracer) {
        return new EventPublisher(kafka, codec, producer, tracer);
    }
}
