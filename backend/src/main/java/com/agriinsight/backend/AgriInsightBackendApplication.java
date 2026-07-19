package com.agriinsight.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "AgriInsight Backend")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AgriInsightBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgriInsightBackendApplication.class, args);
    }
}
