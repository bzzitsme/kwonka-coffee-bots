package com.kwonka.customer.config;

import com.kwonka.common.service.CoffeeShopService;
import com.kwonka.common.service.CustomerNotificationService;
import com.kwonka.common.service.OrderService;
import com.kwonka.customer.bot.CustomerBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@Slf4j
public class TelegramCustomerBotConfig {

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.customer.bot.token}")
    private String customerBotToken;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CoffeeShopService coffeeShopService;

    @Bean(name = "baristaCustomerNotificationService")
    public CustomerNotificationService customerNotificationService() {
        return new CustomerNotificationService(customerBotToken);
    }

    @Bean
    public CustomerBot oneShotCoffeeBot(CustomerNotificationService customerNotificationService) {
        return new CustomerBot(botToken, botUsername, orderService, coffeeShopService, customerNotificationService);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(CustomerBot customerBot) throws TelegramApiException {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(customerBot);
            log.info("Telegram bot {} registered successfully", botUsername);
            return api;
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("Error removing old webhook")) {
                log.warn("Could not remove old webhook. This can happen when running for the first time. Continuing...");
                // Try again with a different approach
                TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
                api.registerBot(customerBot);
                return api;
            }
            throw e;
        }
    }
}