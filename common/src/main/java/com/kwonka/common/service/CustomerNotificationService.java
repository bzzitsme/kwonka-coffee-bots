package com.kwonka.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for sending notifications to customers
 * This service extends DefaultAbsSender from the Telegram Bot API
 * to be able to send messages to customers directly
 */
@Service
@Slf4j
public class CustomerNotificationService extends DefaultAbsSender {

    /**
     * Constructor that takes the customer bot token
     * The bot token is used to authenticate with the Telegram Bot API
     *
     * @param botToken The token of the customer bot
     */
    public CustomerNotificationService(@Value("${telegram.customer.bot.token}") String botToken) {
        super(new DefaultBotOptions(), botToken);
    }

    /**
     * Notifies a customer that their order is ready for pickup
     *
     * @param chatId      Customer's Telegram chat ID
     * @param orderNumber Order number
     */
    public void notifyOrderReady(Long chatId, String orderNumber) {
        if (chatId == null) {
            log.error("Cannot send notification: customer chat ID is null");
            return;
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Ваш кофе ждёт - номер заказа: #" + orderNumber);

        // Add "I've picked it up" button
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Я забрал(а)"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.info("Order ready notification sent to customer {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send order ready notification to customer {}", chatId, e);
        }
    }

    /**
     * Send completion message to customer after they've picked up their order
     *
     * @param chatId Customer's Telegram chat ID
     */
    public void sendOrderCompletionMessage(Long chatId) {
        if (chatId == null) {
            log.error("Cannot send completion message: customer chat ID is null");
            return;
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Спасибо за заказ! Заглядывайте снова - мы уже скучаем 💛");

        // Add "New order" button
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Сделать новый заказ"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.info("Order completion message sent to customer {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send order completion message to customer {}", chatId, e);
        }
    }

    @Override
    public String getBotToken() {
        return super.getBotToken();
    }
}