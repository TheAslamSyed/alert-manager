package com.democorp.warroom.remediation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RemediationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemediationApplication.class, args);
    }
}
