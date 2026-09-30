package com.nexus.auction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.nexus")
@EnableDiscoveryClient
@EnableScheduling
public class AuctionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuctionServiceApplication.class, args);
    }
}
