package com.kwonka.customer.config;

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
public class TelegramBotConfig {

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Autowired
    private OrderService orderService;

    @Bean
    public CustomerBot oneShotCoffeeBot() {
        return new CustomerBot(botToken, botUsername, orderService);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(CustomerBot customerBot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(customerBot);
        log.info("Telegram bot {} registered successfully", botUsername);
        return api;
    }
}