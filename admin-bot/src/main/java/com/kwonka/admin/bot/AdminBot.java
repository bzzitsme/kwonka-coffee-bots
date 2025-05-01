package com.kwonka.admin.bot;

import com.kwonka.admin.service.AdminNotificationService;
import com.kwonka.admin.service.OrderMonitorService;
import com.kwonka.admin.service.StatisticsService;
import com.kwonka.common.entity.Order;
import com.kwonka.common.service.CoffeeShopService;
import com.kwonka.common.service.OrderService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

@Slf4j
public class AdminBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final OrderService orderService;
    private final CoffeeShopService coffeeShopService;
    private final AdminNotificationService adminNotificationService;

    @Setter
    private OrderMonitorService orderMonitorService;

    @Setter
    private StatisticsService statisticsService;

    private final Map<Long, AdminState> adminStates = new HashMap<>();

    private enum AdminState {
        START,
        MONITORING,
        MONITORING_ALL_ORDERS,
        MONITORING_DELAYED_ORDERS,
        VIEWING_STATISTICS
    }

    public AdminBot(String botToken,
                    String botUsername,
                    OrderService orderService,
                    CoffeeShopService coffeeShopService,
                    AdminNotificationService adminNotificationService) {
        super(botToken);
        this.botUsername = botUsername;
        this.orderService = orderService;
        this.coffeeShopService = coffeeShopService;
        this.adminNotificationService = adminNotificationService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            log.debug("Received message: '{}' from chatId: {}", messageText, chatId);

            // Initial command
            if (messageText.equals("/start")) {
                adminStates.put(chatId, AdminState.START);
                sendWelcomeMessage(chatId);
                return;
            }

            // Handle the admin flow based on state
            AdminState currentState = adminStates.getOrDefault(chatId, AdminState.START);

            switch (currentState) {
                case START:
                    if (messageText.equals("Мониторинг заказов")) {
                        adminStates.put(chatId, AdminState.MONITORING);
                        orderMonitorService.registerAdminMonitor(chatId);
                        sendMonitoringStartedMessage(chatId);
                    } else if (messageText.equals("Статистика")) {
                        adminStates.put(chatId, AdminState.VIEWING_STATISTICS);
                        sendStatisticsOptions(chatId);
                    } else {
                        sendUnknownCommandMessage(chatId);
                    }
                    break;

                case MONITORING:
                    if (messageText.equals("Вернуться в главное меню")) {
                        adminStates.put(chatId, AdminState.START);
                        orderMonitorService.unregisterAdminMonitor(chatId);
                        sendWelcomeMessage(chatId);
                    } else if (messageText.equals("Все заказы")) {
                        adminStates.put(chatId, AdminState.MONITORING_ALL_ORDERS);
                        sendAllOrders(chatId);
                    } else if (messageText.equals("Заказы с задержкой")) {
                        adminStates.put(chatId, AdminState.MONITORING_DELAYED_ORDERS);
                        sendDelayedOrders(chatId);
                    } else if (messageText.equals("Проверить заказы")) {
                        sendMonitoringOptions(chatId);
                    } else {
                        sendUnknownCommandMessage(chatId);
                    }
                    break;

                case MONITORING_ALL_ORDERS:
                case MONITORING_DELAYED_ORDERS:
                    if (messageText.equals("Назад")) {
                        adminStates.put(chatId, AdminState.MONITORING);
                        sendMonitoringOptions(chatId);
                    } else {
                        sendUnknownCommandMessage(chatId);
                    }
                    break;

                case VIEWING_STATISTICS:
                    if (messageText.equals("Отчёт за день")) {
                        sendDailyStatistics(chatId);
                    } else if (messageText.equals("Вернуться в главное меню")) {
                        adminStates.put(chatId, AdminState.START);
                        sendWelcomeMessage(chatId);
                    } else {
                        sendUnknownCommandMessage(chatId);
                    }
                    break;

                default:
                    sendUnknownCommandMessage(chatId);
                    break;
            }
        } else if (update.hasCallbackQuery()) {
            // Handle callback queries from inline buttons
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            log.debug("Received callback: '{}' from admin chatId: {}", callbackData, chatId);

            if (callbackData.startsWith("notify_barista:")) {
                String[] data = callbackData.substring("notify_barista:".length()).split(":");
                String orderNumber = data[0];
                Long baristaChatId = Long.parseLong(data[1]);

                handleNotifyBarista(chatId, orderNumber, baristaChatId);
            }
        }
    }

    private void sendWelcomeMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Добро пожаловать в панель администратора One Shott Coffee! ☕\n" +
                "Я слежу за всеми заказами в реальном времени.");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Мониторинг заказов"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Статистика"));
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Welcome message sent to admin chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending welcome message to admin chatId: {}", chatId, e);
        }
    }

    private void sendMonitoringStartedMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Мониторинг заказов активирован.\n" +
                "Вы будете получать уведомления о заказах, которые ожидают более 5 минут.");

        sendMonitoringOptions(chatId);
    }

    private void sendMonitoringOptions(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выберите опцию мониторинга:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Все заказы"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Заказы с задержкой"));
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("Вернуться в главное меню"));
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Monitoring options sent to admin chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending monitoring options to admin chatId: {}", chatId, e);
        }
    }

    private void sendAllOrders(long chatId) {
        List<Order> pendingOrders = orderService.getOrdersByStatus(Order.OrderStatus.PENDING);

        if (pendingOrders.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("На данный момент нет активных заказов.\n\nНажмите кнопку \"Назад\" для возврата в меню мониторинга.");

            // Add back button
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            keyboardMarkup.setResizeKeyboard(true);
            keyboardMarkup.setOneTimeKeyboard(false);
            keyboardMarkup.setSelective(true);

            List<KeyboardRow> keyboard = new ArrayList<>();
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton("Назад"));
            keyboard.add(row);

            keyboardMarkup.setKeyboard(keyboard);
            message.setReplyMarkup(keyboardMarkup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending no orders message to admin chatId: {}", chatId, e);
            }
            return;
        }

        // Sort orders by creation time (newest first)
        pendingOrders.sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));

        StringBuilder messageText = new StringBuilder();
        messageText.append("📋 *Все активные заказы:*\n\n");

        for (Order order : pendingOrders) {
            // Calculate wait time
            long waitTimeMinutes = java.time.Duration.between(order.getCreatedAt(), java.time.LocalDateTime.now()).toMinutes();

            String waitTimeIndicator = waitTimeMinutes >= 10 ? "⚠️ " :
                    waitTimeMinutes >= 5 ? "⏰ " : "✅ ";

            messageText.append(String.format(
                    "%s*Заказ #%s* - ожидание: %d мин.\n" +
                            "☕ %s (%s)\n" +
                            "%s\n" +
                            "%s\n" +
                            "💰 %s ₸\n" +
                            "🏢 %s\n\n",
                    waitTimeIndicator,
                    order.getOrderNumber(),
                    waitTimeMinutes,
                    order.getCoffeeType(),
                    order.getSize(),
                    order.getMilkType() != null ? "🥛 " + order.getMilkType() : "",
                    order.getSyrupType() != null ? "🍯 " + order.getSyrupType() : "",
                    order.getTotalPrice(),
                    order.getCoffeeShop().getName()
            ));
        }

        messageText.append("\n*Обозначения:*\n" +
                "✅ - Меньше 5 минут ожидания\n" +
                "⏰ - 5-10 минут ожидания\n" +
                "⚠️ - Более 10 минут ожидания\n\n" +
                "Для уведомления баристы, перейдите в \"Заказы с задержкой\".");

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText.toString());
        message.setParseMode(ParseMode.MARKDOWN);

        // Add back button
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Назад"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("All orders sent to admin chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending all orders to admin chatId: {}", chatId, e);
        }
    }

    private void sendDelayedOrders(long chatId) {
        List<Map.Entry<Order, Integer>> delayedOrders = orderMonitorService.getDelayedPendingOrders();

        if (delayedOrders.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("На данный момент нет заказов, ожидающих более 5 минут.\n\nНажмите кнопку \"Назад\" для возврата в меню мониторинга.");

            // Add back button
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            keyboardMarkup.setResizeKeyboard(true);
            keyboardMarkup.setOneTimeKeyboard(false);
            keyboardMarkup.setSelective(true);

            List<KeyboardRow> keyboard = new ArrayList<>();
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton("Назад"));
            keyboard.add(row);

            keyboardMarkup.setKeyboard(keyboard);
            message.setReplyMarkup(keyboardMarkup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending no delayed orders message to admin chatId: {}", chatId, e);
            }
            return;
        }

        // Sort orders by wait time (descending)
        delayedOrders.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));

        // First, add the back button
        SendMessage menuMessage = new SendMessage();
        menuMessage.setChatId(chatId);
        menuMessage.setText("Заказы с задержкой более 5 минут:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Назад"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        menuMessage.setReplyMarkup(keyboardMarkup);

        try {
            execute(menuMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending delayed orders menu to admin chatId: {}", chatId, e);
        }

        // Now send each delayed order as a separate message with notification button
        for (Map.Entry<Order, Integer> entry : delayedOrders) {
            Order order = entry.getKey();
            int waitTimeMinutes = entry.getValue();

            sendDelayedOrderInfo(chatId, order, waitTimeMinutes);
        }
    }

    private void sendStatisticsOptions(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выберите опцию статистики:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Отчёт за день"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Вернуться в главное меню"));
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Statistics options sent to admin chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending statistics options to admin chatId: {}", chatId, e);
        }
    }

    private void sendDailyStatistics(long chatId) {
        StatisticsService.DailyStats stats = statisticsService.getCurrentDayStats();
        String messageText = statisticsService.formatDailyStats(stats);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        message.setParseMode(ParseMode.MARKDOWN);

        try {
            execute(message);
            log.debug("Daily statistics sent to admin chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending daily statistics to admin chatId: {}", chatId, e);
        }
    }

    private void sendDelayedOrderInfo(long chatId, Order order, int waitTimeMinutes) {
        String messageText = String.format(
                "⏱ *Простой заказа* ⏱\n\n" +
                        "*Заказ #%s ожидает %d минут.*\n" +
                        "☕ %s (%s)\n" +
                        "%s\n" +
                        "%s\n" +
                        "💰 %s ₸\n" +
                        "🏢 Кофейня: %s\n\n" +
                        "Нажмите кнопку, чтобы отправить уведомление баристе.",
                order.getOrderNumber(),
                waitTimeMinutes,
                order.getCoffeeType(),
                order.getSize(),
                order.getMilkType() != null ? "🥛 " + order.getMilkType() : "",
                order.getSyrupType() != null ? "🍯 " + order.getSyrupType() : "",
                order.getTotalPrice(),
                order.getCoffeeShop().getName()
        );

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        message.setParseMode(ParseMode.MARKDOWN);

        // Create button to notify barista
        // For demonstration, we're using a fixed barista chat ID
        // In a real application, you'd lookup the active barista for this coffee shop
        long baristaChatId = 987654321L; // Replace with actual lookup logic

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton notifyButton = new InlineKeyboardButton();
        notifyButton.setText("Уведомить баристу");
        notifyButton.setCallbackData("notify_barista:" + order.getOrderNumber() + ":" + baristaChatId);
        row.add(notifyButton);

        rows.add(row);
        inlineKeyboardMarkup.setKeyboard(rows);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
            log.debug("Delayed order info sent to admin chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending delayed order info to admin chatId: {}", chatId, e);
        }
    }

    private void handleNotifyBarista(long adminChatId, String orderNumber, Long baristaChatId) {
        try {
            Optional<Order> orderOpt = orderService.getOrderByNumber(orderNumber);
            if (orderOpt.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(adminChatId);
                message.setText("Заказ #" + orderNumber + " не найден.");
                execute(message);
                return;
            }

            Order order = orderOpt.get();

            // Check if the order is still pending
            if (order.getStatus() != Order.OrderStatus.PENDING) {
                SendMessage message = new SendMessage();
                message.setChatId(adminChatId);
                message.setText("Заказ #" + orderNumber + " уже был принят баристой или отменен.");
                execute(message);
                return;
            }

            // Get wait time in minutes
            long waitTimeMinutes = java.time.Duration.between(order.getCreatedAt(), java.time.LocalDateTime.now()).toMinutes();

            // Notify barista
            adminNotificationService.notifyBarista(baristaChatId, order, (int) waitTimeMinutes);

            // Confirm to admin
            SendMessage message = new SendMessage();
            message.setChatId(adminChatId);
            message.setText("Уведомление о заказе #" + orderNumber + " отправлено баристе.");
            execute(message);

            log.info("Admin {} notified barista {} about order {}", adminChatId, baristaChatId, orderNumber);
        } catch (Exception e) {
            log.error("Error notifying barista about order", e);
            try {
                SendMessage message = new SendMessage();
                message.setChatId(adminChatId);
                message.setText("Ошибка при отправке уведомления баристе.");
                execute(message);
            } catch (TelegramApiException ex) {
                log.error("Error sending error message to admin", ex);
            }
        }
    }

    private void sendUnknownCommandMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Извините, я не понимаю эту команду. Пожалуйста, воспользуйтесь предложенными кнопками или отправьте /start, чтобы начать заново.");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending unknown command message to admin chatId: {}", chatId, e);
        }
    }

    /**
     * Scheduled task to automatically check for delayed orders and notify admins
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void autoCheckDelayedOrders() {
        List<Map.Entry<Order, Integer>> delayedOrders = orderMonitorService.getDelayedPendingOrders();

        if (delayedOrders.isEmpty()) {
            return;
        }

        // Only consider orders waiting more than 10 minutes
        List<Map.Entry<Order, Integer>> criticalOrders = delayedOrders.stream()
                .filter(entry -> entry.getValue() >= 10)
                .sorted((o1, o2) -> o2.getValue().compareTo(o1.getValue()))
                .toList();

        if (criticalOrders.isEmpty()) {
            return;
        }

        // Notify all active admin monitors
        for (Long adminChatId : orderMonitorService.getActiveAdminMonitors()) {
            for (Map.Entry<Order, Integer> entry : criticalOrders) {
                Order order = entry.getKey();
                int waitTimeMinutes = entry.getValue();

                // Only send notification if we haven't already notified about this order
                if (!orderMonitorService.isOrderAlreadyNotified(order.getOrderNumber())) {
                    sendDelayedOrderInfo(adminChatId, order, waitTimeMinutes);
                    orderMonitorService.markOrderAsNotified(order.getOrderNumber());
                }
            }
        }
    }
}