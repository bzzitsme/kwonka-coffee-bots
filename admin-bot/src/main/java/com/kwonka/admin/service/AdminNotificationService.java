package com.kwonka.admin.service;

import com.kwonka.common.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for sending notifications to baristas from the admin bot
 */
@Service
@Slf4j
public class AdminNotificationService extends DefaultAbsSender {

    private final String baristaBotToken;

    /**
     * Constructor that takes the barista bot token
     */
    public AdminNotificationService(@Value("${telegram.bot.barista.token}") String botToken) {
        super(new DefaultBotOptions(), botToken);
        this.baristaBotToken = botToken;
    }

    /**
     * Sends a notification to a barista about a pending order
     *
     * @param baristaChatId   The barista's chat ID
     * @param order           The order details
     * @param waitTimeMinutes How long the order has been waiting
     */
    public void notifyBarista(Long baristaChatId, Order order, int waitTimeMinutes) {
        if (baristaChatId == null) {
            log.error("Cannot send notification: barista chat ID is null");
            return;
        }

        String messageText = String.format(
                "⚠️ *НАПОМИНАНИЕ ОТ АДМИНИСТРАТОРА* ⚠️\n\n" +
                        "*Заказ #%s ожидает уже %d минут!*\n" +
                        "☕ %s (%s)\n" +
                        "%s\n" +
                        "%s\n" +
                        "💰 %s ₸\n\n" +
                        "Пожалуйста, примите заказ в работу!",
                order.getOrderNumber(),
                waitTimeMinutes,
                order.getCoffeeType(),
                order.getSize(),
                order.getMilkType() != null ? "🥛 " + order.getMilkType() : "",
                order.getSyrupType() != null ? "🍯 " + order.getSyrupType() : "",
                order.getTotalPrice()
        );

        SendMessage message = new SendMessage();
        message.setChatId(baristaChatId);
        message.setText(messageText);
        message.setParseMode(ParseMode.MARKDOWN);

        // Add button for taking the order
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton takeButton = new InlineKeyboardButton();
        takeButton.setText("✅ Принять заказ");
        takeButton.setCallbackData("take_order:" + order.getOrderNumber());
        row.add(takeButton);

        rows.add(row);
        inlineKeyboardMarkup.setKeyboard(rows);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
            log.info("Admin notification sent to barista {}", baristaChatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send admin notification to barista {}", baristaChatId, e);
        }
    }

    @Override
    public String getBotToken() {
        return baristaBotToken;
    }
}