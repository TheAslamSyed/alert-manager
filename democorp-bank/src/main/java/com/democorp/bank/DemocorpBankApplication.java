package com.democorp.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DemoCorp Bank: the fictional bank the War Room watches.
 * One app with three logical parts (banking-api, payment-service, account-service) and a card-network stub.
 * Every metric and log line carries the part's service name, so the War Room sees three services.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class DemocorpBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemocorpBankApplication.class, args);
    }
}
