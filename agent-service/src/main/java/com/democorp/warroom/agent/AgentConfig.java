package com.democorp.warroom.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AgentConfig {

    /** Investigations and agents run on virtual threads, so a slow LLM never blocks Kafka consumers. */
    @Bean(destroyMethod = "shutdown")
    ExecutorService agentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Read-only tool calls (Prometheus, Loki, knowledge search) with short timeouts. */
    @Bean
    RestClient toolClient(RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return builder.requestFactory(factory).build();
    }
}
