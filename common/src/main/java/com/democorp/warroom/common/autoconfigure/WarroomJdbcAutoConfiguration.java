package com.democorp.warroom.common.autoconfigure;

import com.democorp.warroom.common.messaging.IdempotencyGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(JdbcTemplate.class)
public class WarroomJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    IdempotencyGuard idempotencyGuard(JdbcTemplate jdbc) {
        return new IdempotencyGuard(jdbc);
    }
}
