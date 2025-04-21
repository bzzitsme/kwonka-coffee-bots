package com.kwonka.barista.config;

import com.kwonka.barista.bot.BaristaBot;
import com.kwonka.common.service.CoffeeShopService;
import com.kwonka.common.service.OrderService;
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

    @Value("${telegram.bot.barista.username}")
    private String botUsername;

    @Value("${telegram.bot.barista.token}")
    private String botToken;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CoffeeShopService coffeeShopService;

    @Bean
    public BaristaBot baristaBot() {
        return new BaristaBot(botToken, botUsername, orderService, coffeeShopService);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(BaristaBot baristaBot) throws TelegramApiException {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(baristaBot);
            log.info("Barista Telegram bot {} registered successfully", botUsername);
            return api;
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("Error removing old webhook")) {
                log.warn("Could not remove old webhook. This can happen when running for the first time. Continuing...");
                // Try again with a different approach
                TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
                api.registerBot(baristaBot);
                return api;
            }
            throw e;
        }
    }
}