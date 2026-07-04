package com.turkcell.usageservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: OutboxPoller (quota-events publish) @Scheduled ile calisir.
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class UsageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsageServiceApplication.class, args);
    }
}
