package com.kwonka.barista.bot;

import com.kwonka.common.entity.CoffeeShop;
import com.kwonka.common.entity.Order;
import com.kwonka.common.service.CoffeeShopService;
import com.kwonka.common.service.CustomerNotificationService;
import com.kwonka.common.service.OrderService;
import lombok.extern.slf4j.Slf4j;
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

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class BaristaBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final OrderService orderService;
    private final CoffeeShopService coffeeShopService;
    private final CustomerNotificationService customerNotificationService;

    private final Map<Long, BaristaState> baristaStates = new HashMap<>();
    private final Map<Long, String> baristaLocations = new HashMap<>();

    private enum BaristaState {
        START,
        LOCATION_SELECTION,
        VIEWING_ORDERS,
        ORDER_DETAILS
    }

    public BaristaBot(String botToken, String botUsername, OrderService orderService,
                      CoffeeShopService coffeeShopService,
                      CustomerNotificationService customerNotificationService) {
        super(botToken);
        this.botUsername = botUsername;
        this.orderService = orderService;
        this.coffeeShopService = coffeeShopService;
        this.customerNotificationService = customerNotificationService;
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

            log.debug("Received message: '{}' from barista chatId: {}", messageText, chatId);

            // Initial command
            if (messageText.equals("/start")) {
                baristaStates.put(chatId, BaristaState.START);
                baristaLocations.remove(chatId);
                sendWelcomeMessage(chatId);
                return;
            }

            // Handle the barista flow based on state
            BaristaState currentState = baristaStates.getOrDefault(chatId, BaristaState.START);

            switch (currentState) {
                case START:
                    if (messageText.equals("Начать работу")) {
                        baristaStates.put(chatId, BaristaState.LOCATION_SELECTION);
                        sendLocationSelectionMessage(chatId);
                    } else {
                        sendUnknownCommandMessage(chatId);
                    }
                    break;

                case LOCATION_SELECTION:
                    // Find the coffee shop by name
                    Optional<CoffeeShop> coffeeShopOpt = coffeeShopService.getAllActiveShops().stream()
                            .filter(shop -> shop.getName().equals(messageText))
                            .findFirst();

                    if (coffeeShopOpt.isPresent()) {
                        baristaLocations.put(chatId, coffeeShopOpt.get().getCode());
                        baristaStates.put(chatId, BaristaState.VIEWING_ORDERS);
                        sendLocationConfirmation(chatId, coffeeShopOpt.get().getName());
                        sendPendingOrders(chatId);
                    } else {
                        sendInvalidLocationMessage(chatId);
                    }
                    break;

                case VIEWING_ORDERS:
                    switch (messageText) {
                        case "Обновить заказы" -> sendPendingOrders(chatId);
                        case "Сменить локацию" -> {
                            baristaStates.put(chatId, BaristaState.LOCATION_SELECTION);
                            sendLocationSelectionMessage(chatId);
                        }
                        case "Заказы в работе" -> sendInProgressOrders(chatId);
                        default -> sendUnknownCommandMessage(chatId);
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

            log.debug("Received callback: '{}' from barista chatId: {}", callbackData, chatId);

            if (callbackData.startsWith("take_order:")) {
                String orderNumber = callbackData.substring("take_order:".length());
                handleTakeOrder(chatId, orderNumber);
            } else if (callbackData.startsWith("ready_order:")) {
                String orderNumber = callbackData.substring("ready_order:".length());
                handleOrderReady(chatId, orderNumber);
            } else if (callbackData.startsWith("view_order:")) {
                String orderNumber = callbackData.substring("view_order:".length());
                sendOrderDetails(chatId, orderNumber);
            }
        }
    }

    private void sendWelcomeMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Добро пожаловать в бота для барист One Shott Coffee! ☕\n" +
                "Нажмите кнопку, чтобы начать работу.");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Начать работу"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Welcome message sent to barista chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending welcome message to barista chatId: {}", chatId, e);
        }
    }

    private void sendLocationSelectionMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("В какой кофейне вы работаете?");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        List<CoffeeShop> coffeeShops = coffeeShopService.getAllActiveShops();

        for (CoffeeShop shop : coffeeShops) {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(shop.getName()));
            keyboard.add(row);
        }

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Location selection message sent to barista chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending location selection message to barista chatId: {}", chatId, e);
        }
    }

    private void sendLocationConfirmation(long chatId, String location) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Вы выбрали локацию: " + location + "\n" +
                "Супер! Вы будете получать заказы только из этой локации.");

        try {
            execute(message);
            log.debug("Location confirmation sent to barista chatId: {}", chatId);
            baristaStates.put(chatId, BaristaState.VIEWING_ORDERS);
        } catch (TelegramApiException e) {
            log.error("Error sending location confirmation to barista chatId: {}", chatId, e);
        }
    }

    private void sendInvalidLocationMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Пожалуйста, выберите локацию из предложенных вариантов.");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending invalid location message to barista chatId: {}", chatId, e);
        }
    }

    private void sendPendingOrders(long chatId) {
        String locationCode = baristaLocations.get(chatId);
        if (locationCode == null) {
            sendLocationSelectionMessage(chatId);
            return;
        }

        // Get the coffee shop by code
        Optional<CoffeeShop> coffeeShopOpt = coffeeShopService.getShopByCode(locationCode);
        if (coffeeShopOpt.isEmpty()) {
            sendLocationSelectionMessage(chatId);
            return;
        }

        CoffeeShop coffeeShop = coffeeShopOpt.get();

        // Get pending orders for this coffee shop
        List<Order> pendingOrders = orderService.getOrdersByShopAndStatus(coffeeShop, Order.OrderStatus.PENDING);

        if (pendingOrders.isEmpty()) {
            sendNoOrdersMessage(chatId, "новых");
            return;
        }

        // Create a message with orders list
        StringBuilder messageText = new StringBuilder();
        messageText.append("📋 *Новые заказы:*\n\n");

        // Create inline keyboard with order buttons
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Order order : pendingOrders) {
            String orderInfo = String.format(
                    "*Заказ #%s*\n" +
                            "☕ %s (%s)\n" +
                            "%s\n" +
                            "%s\n" +
                            "💰 %s ₸\n\n",
                    order.getOrderNumber(),
                    order.getCoffeeType(),
                    order.getSize(),
                    order.getMilkType() != null ? "🥛 " + order.getMilkType() : "",
                    order.getSyrupType() != null ? "🍯 " + order.getSyrupType() : "",
                    order.getTotalPrice()
            );

            messageText.append(orderInfo);

            // Add button for each order
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton viewButton = new InlineKeyboardButton();
            viewButton.setText("🔍 #" + order.getOrderNumber());
            viewButton.setCallbackData("view_order:" + order.getOrderNumber());
            row.add(viewButton);

            InlineKeyboardButton takeButton = new InlineKeyboardButton();
            takeButton.setText("✅ Принять");
            takeButton.setCallbackData("take_order:" + order.getOrderNumber());
            row.add(takeButton);

            rows.add(row);
        }

        inlineKeyboardMarkup.setKeyboard(rows);

        // Send the message with orders
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText.toString());
        message.setParseMode(ParseMode.MARKDOWN);
        message.setReplyMarkup(inlineKeyboardMarkup);

        // Also add reply keyboard with refresh button
        ReplyKeyboardMarkup replyKeyboardMarkup = getOrdersMenuKeyboard();

        SendMessage menuMessage = new SendMessage();
        menuMessage.setChatId(chatId);
        menuMessage.setText("Меню:");
        menuMessage.setReplyMarkup(replyKeyboardMarkup);

        try {
            execute(message);
            execute(menuMessage);
            log.debug("Pending orders sent to barista chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending pending orders to barista chatId: {}", chatId, e);
        }
    }

    private void sendInProgressOrders(long chatId) {
        String locationCode = baristaLocations.get(chatId);
        if (locationCode == null) {
            sendLocationSelectionMessage(chatId);
            return;
        }

        // Get the coffee shop by code
        Optional<CoffeeShop> coffeeShopOpt = coffeeShopService.getShopByCode(locationCode);
        if (coffeeShopOpt.isEmpty()) {
            sendLocationSelectionMessage(chatId);
            return;
        }

        CoffeeShop coffeeShop = coffeeShopOpt.get();

        // Get in-progress orders for this coffee shop
        List<Order> inProgressOrders = orderService.getOrdersByShopAndStatus(coffeeShop, Order.OrderStatus.IN_PREPARATION);

        if (inProgressOrders.isEmpty()) {
            sendNoOrdersMessage(chatId, "в работе");
            return;
        }

        // Create a message with orders list
        StringBuilder messageText = new StringBuilder();
        messageText.append("🔄 *Заказы в работе:*\n\n");

        // Create inline keyboard with order buttons
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Order order : inProgressOrders) {
            String orderInfo = String.format(
                    "*Заказ #%s*\n" +
                            "☕ %s (%s)\n" +
                            "%s\n" +
                            "%s\n" +
                            "💰 %s ₸\n\n",
                    order.getOrderNumber(),
                    order.getCoffeeType(),
                    order.getSize(),
                    order.getMilkType() != null ? "🥛 " + order.getMilkType() : "",
                    order.getSyrupType() != null ? "🍯 " + order.getSyrupType() : "",
                    order.getTotalPrice()
            );

            messageText.append(orderInfo);

            // Add button for each order
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton viewButton = new InlineKeyboardButton();
            viewButton.setText("🔍 #" + order.getOrderNumber());
            viewButton.setCallbackData("view_order:" + order.getOrderNumber());
            row.add(viewButton);

            InlineKeyboardButton readyButton = new InlineKeyboardButton();
            readyButton.setText("✅ Готов");
            readyButton.setCallbackData("ready_order:" + order.getOrderNumber());
            row.add(readyButton);

            rows.add(row);
        }

        inlineKeyboardMarkup.setKeyboard(rows);

        // Send the message with orders
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText.toString());
        message.setParseMode(ParseMode.MARKDOWN);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
            log.debug("In-progress orders sent to barista chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending in-progress orders to barista chatId: {}", chatId, e);
        }
    }

    private void sendNoOrdersMessage(long chatId, String orderType) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("На данный момент нет " + orderType + " заказов. Ожидайте новых заказов.");

        // Add the orders menu keyboard to allow refresh and other options
        ReplyKeyboardMarkup keyboardMarkup = getOrdersMenuKeyboard();
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("No orders message sent to barista chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending no orders message to barista chatId: {}", chatId, e);
        }
    }

    private void sendUnknownCommandMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Извините, я не понимаю эту команду. Пожалуйста, воспользуйтесь предложенными кнопками или отправьте /start, чтобы начать заново.");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending unknown command message to barista chatId: {}", chatId, e);
        }
    }

    private void sendOrderDetails(long chatId, String orderNumber) {
        Optional<Order> orderOpt = orderService.getOrderByNumber(orderNumber);

        if (orderOpt.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Заказ #" + orderNumber + " не найден.");

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending order not found message to barista chatId: {}", chatId, e);
            }
            return;
        }

        Order order = orderOpt.get();

        // Format order details
        StringBuilder messageText = new StringBuilder();
        messageText.append("*Детали заказа #").append(order.getOrderNumber()).append("*\n\n");
        messageText.append("☕ *Напиток:* ").append(order.getCoffeeType()).append("\n");
        messageText.append("📏 *Размер:* ").append(order.getSize()).append("\n");

        if (order.getMilkType() != null && !order.getMilkType().isEmpty()) {
            messageText.append("🥛 *Молоко:* ").append(order.getMilkType()).append("\n");
        }

        if (order.getSyrupType() != null && !order.getSyrupType().isEmpty()) {
            messageText.append("🍯 *Сироп:* ").append(order.getSyrupType()).append("\n");
        }

        messageText.append("💰 *Стоимость:* ").append(order.getTotalPrice()).append(" ₸\n");
        messageText.append("🔄 *Статус:* ").append(getStatusEmoji(order.getStatus())).append(" ").append(getStatusText(order.getStatus())).append("\n");
        messageText.append("🏢 *Кофейня:* ").append(order.getCoffeeShop().getName()).append("\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        messageText.append("⏱ *Создан:* ").append(order.getCreatedAt().format(formatter)).append("\n");

        // Create buttons based on order status
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        if (order.getStatus() == Order.OrderStatus.PENDING) {
            InlineKeyboardButton takeButton = new InlineKeyboardButton();
            takeButton.setText("✅ Принять заказ");
            takeButton.setCallbackData("take_order:" + order.getOrderNumber());
            row.add(takeButton);
        } else if (order.getStatus() == Order.OrderStatus.IN_PREPARATION) {
            InlineKeyboardButton readyButton = new InlineKeyboardButton();
            readyButton.setText("✅ Заказ готов");
            readyButton.setCallbackData("ready_order:" + order.getOrderNumber());
            row.add(readyButton);
        }

        rows.add(row);
        inlineKeyboardMarkup.setKeyboard(rows);

        // Send the message with order details
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText.toString());
        message.setParseMode(ParseMode.MARKDOWN);

        if (!row.isEmpty()) {
            message.setReplyMarkup(inlineKeyboardMarkup);
        }

        try {
            execute(message);
            log.debug("Order details sent to barista chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending order details to barista chatId: {}", chatId, e);
        }
    }

    private void handleTakeOrder(long chatId, String orderNumber) {
        try {
            Order updatedOrder = orderService.updateOrderStatus(orderNumber, Order.OrderStatus.IN_PREPARATION);

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("✅ Вы приняли заказ #" + orderNumber + " в работу!");
            message.setParseMode(ParseMode.MARKDOWN);

            execute(message);
            log.info("Barista {} took order {} into preparation", chatId, orderNumber);

            // Refresh the orders list
            sendPendingOrders(chatId);

        } catch (Exception e) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("❌ Ошибка при принятии заказа #" + orderNumber + ". Попробуйте еще раз.");

            try {
                execute(message);
            } catch (TelegramApiException ex) {
                log.error("Error sending error message to barista chatId: {}", chatId, ex);
            }

            log.error("Error taking order {} by barista {}", orderNumber, chatId, e);
        }
    }

    private void handleOrderReady(long chatId, String orderNumber) {
        try {
            Order updatedOrder = orderService.updateOrderStatus(orderNumber, Order.OrderStatus.READY);

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("✨ Заказ #" + orderNumber + " отмечен как готовый! Клиент получил уведомление.");
            message.setParseMode(ParseMode.MARKDOWN);

            execute(message);
            log.info("Barista {} marked order {} as ready", chatId, orderNumber);

            // Notify the customer that their order is ready
            notifyCustomerOrderReady(updatedOrder);

            // Refresh the in-progress orders list
            sendInProgressOrders(chatId);

        } catch (Exception e) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("❌ Ошибка при обновлении статуса заказа #" + orderNumber + ". Попробуйте еще раз.");

            try {
                execute(message);
            } catch (TelegramApiException ex) {
                log.error("Error sending error message to barista chatId: {}", chatId, ex);
            }

            log.error("Error marking order {} as ready by barista {}", orderNumber, chatId, e);
        }
    }

    private ReplyKeyboardMarkup getOrdersMenuKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Обновить заказы"));
        row1.add(new KeyboardButton("Заказы в работе"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Сменить локацию"));
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private String getStatusText(Order.OrderStatus status) {
        switch (status) {
            case PENDING:
                return "Ожидает";
            case IN_PREPARATION:
                return "В работе";
            case READY:
                return "Готов";
            case COMPLETED:
                return "Завершен";
            case CANCELLED:
                return "Отменен";
            default:
                return "Неизвестно";
        }
    }

    private String getStatusEmoji(Order.OrderStatus status) {
        switch (status) {
            case PENDING:
                return "⏳";
            case IN_PREPARATION:
                return "🔄";
            case READY:
                return "✅";
            case COMPLETED:
                return "🎉";
            case CANCELLED:
                return "❌";
            default:
                return "❓";
        }
    }

    private void notifyCustomerOrderReady(Order order) {
        // Get customer Telegram chat ID from the order's customerId
        Long customerChatId = order.getCustomerId();
        if (customerChatId == null) {
            log.error("Cannot notify customer: no customer ID found for order {}", order.getOrderNumber());
            return;
        }

        // Use the notification service to send message to customer
        customerNotificationService.notifyOrderReady(customerChatId, order.getOrderNumber());
        log.info("Customer {} notified about ready order {}", customerChatId, order.getOrderNumber());
    }
}