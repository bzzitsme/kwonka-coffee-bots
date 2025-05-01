package com.kwonka.admin.config;

import com.kwonka.admin.bot.AdminBot;
import com.kwonka.admin.service.AdminNotificationService;
import com.kwonka.admin.service.OrderMonitorService;
import com.kwonka.admin.service.StatisticsService;
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
public class TelegramAdminBotConfig {

    @Value("${telegram.bot.admin.username}")
    private String botUsername;

    @Value("${telegram.bot.admin.token}")
    private String botToken;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CoffeeShopService coffeeShopService;

    @Autowired
    private AdminNotificationService adminNotificationService;

    @Autowired
    private OrderMonitorService orderMonitorService;

    @Autowired
    private StatisticsService statisticsService;

    @Bean
    public AdminBot adminBot() {
        AdminBot bot = new AdminBot(
                botToken,
                botUsername,
                orderService,
                coffeeShopService,
                adminNotificationService
        );
        bot.setOrderMonitorService(orderMonitorService);
        bot.setStatisticsService(statisticsService);
        return bot;
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(AdminBot adminBot) throws TelegramApiException {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(adminBot);
            log.info("Admin Telegram bot {} registered successfully", botUsername);
            return api;
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("Error removing old webhook")) {
                log.warn("Could not remove old webhook. This can happen when running for the first time. Continuing...");
                // Try again with a different approach
                TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
                api.registerBot(adminBot);
                return api;
            }
            throw e;
        }
    }
}