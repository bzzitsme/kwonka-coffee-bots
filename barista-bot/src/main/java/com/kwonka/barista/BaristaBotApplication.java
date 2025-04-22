package com.kwonka.barista;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.kwonka.barista", "com.kwonka.common.service"})
@EntityScan("com.kwonka.common.entity")
@EnableJpaRepositories("com.kwonka.common.repository")
public class BaristaBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(BaristaBotApplication.class, args);
    }
}