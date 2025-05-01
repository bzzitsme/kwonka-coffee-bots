package com.kwonka.admin;

import com.kwonka.common.service.CustomerNotificationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(
        basePackages = {
                "com.kwonka.admin",
                "com.kwonka.common.service",
                "com.kwonka.common.repository"
        },
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = CustomerNotificationService.class
                )
        }
)
@EntityScan("com.kwonka.common.entity")
@EnableJpaRepositories("com.kwonka.common.repository")
@EnableScheduling
public class AdminBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminBotApplication.class, args);
    }
}