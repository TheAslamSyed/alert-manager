package com.democorp.warroom.common.autoconfigure;

import com.democorp.warroom.common.catalog.ServiceCatalog;
import com.democorp.warroom.common.events.EventCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@EnableConfigurationProperties(ServiceCatalog.class)
public class WarroomCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    EventCodec eventCodec(ObjectMapper mapper) {
        return new EventCodec(mapper);
    }
}
