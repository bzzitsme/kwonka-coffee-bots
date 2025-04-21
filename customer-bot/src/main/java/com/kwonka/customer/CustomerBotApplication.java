package com.kwonka.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.kwonka.customer", "com.kwonka.common"})
@EntityScan("com.kwonka.common.entity")
@EnableJpaRepositories("com.kwonka.common.repository")
public class CustomerBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerBotApplication.class, args);
    }
}