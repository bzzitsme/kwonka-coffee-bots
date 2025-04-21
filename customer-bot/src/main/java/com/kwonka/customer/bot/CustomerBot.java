package com.kwonka.customer.bot;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

@Slf4j
public class CustomerBot extends TelegramLongPollingBot {

    private final String botUsername;

    private final Map<Long, Map<String, String>> userSelections = new HashMap<>();

    private final Map<Long, UserState> userStates = new HashMap<>();

    public CustomerBot(String botToken, String botUsername) {
        super(botToken);
        this.botUsername = botUsername;
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

            if (messageText.equals("/start")) {
                // Reset user state
                userStates.put(chatId, UserState.START);
                clearUserSelections(chatId);
                sendWelcomeMessage(chatId);
            } else if (messageText.equals("Новый заказ")) {
                // Clear previous selections and start ordering process
                clearUserSelections(chatId);
                userStates.put(chatId, UserState.SELECTING_COFFEE);
                sendCoffeeSelectionPage(chatId);
            } else {
                // Get current state and handle accordingly
                UserState currentState = userStates.getOrDefault(chatId, UserState.START);

                switch (currentState) {
                    case START:
                        if (messageText.equals("Старт")) {
                            userStates.put(chatId, UserState.INTRO);
                            sendSecondPage(chatId);
                        } else {
                            handleUnknownCommand(chatId);
                        }
                        break;

                    case INTRO:
                        if (messageText.equals("Хочу кофе")) {
                            userStates.put(chatId, UserState.SELECTING_COFFEE);
                            sendCoffeeSelectionPage(chatId);
                        } else {
                            handleUnknownCommand(chatId);
                        }
                        break;

                    case SELECTING_COFFEE:
                        if (isValidCoffeeType(messageText)) {
                            saveUserSelection(chatId, "coffeeType", messageText);
                            userStates.put(chatId, UserState.SELECTING_SIZE);
                            sendSizeSelectionPage(chatId);
                        } else {
                            handleUnknownCommand(chatId);
                        }
                        break;

                    case SELECTING_SIZE:
                        if (isValidSize(messageText)) {
                            saveUserSelection(chatId, "size", messageText);
                            userStates.put(chatId, UserState.SELECTING_ADDONS);
                            sendAddonsSelectionPage(chatId);
                        } else {
                            handleUnknownCommand(chatId);
                        }
                        break;

                    case SELECTING_ADDONS:
                        handleAddonsSelection(chatId, messageText);
                        break;

                    case SELECTING_MILK:
                        if (isValidMilkType(messageText)) {
                            saveUserSelection(chatId, "milkType", messageText);
                            userStates.put(chatId, UserState.SELECTING_ADDONS);
                            sendAddonsSelectionPage(chatId);
                        } else {
                            handleUnknownCommand(chatId);
                        }
                        break;

                    case SELECTING_SYRUP:
                        if (isValidSyrupType(messageText)) {
                            saveUserSelection(chatId, "syrupType", messageText);
                            userStates.put(chatId, UserState.SELECTING_ADDONS);
                            sendAddonsSelectionPage(chatId);
                        } else {
                            handleUnknownCommand(chatId);
                        }
                        break;

                    case CONFIRMING_ORDER:
                        handleOrderConfirmation(chatId, messageText);
                        break;

                    case PAYMENT_INIT:
                        if (messageText.equals("Оплатить")) {
                            userStates.put(chatId, UserState.PAYMENT_CONFIRM);
                            sendPaymentConfirmPage(chatId);
                        } else {
                            handleUnknownCommand(chatId);
                        }
                        break;

                    case PAYMENT_CONFIRM:
                        if (messageText.equals("Я оплатил(а)")) {
                            // Payment successful (mock)
                            userStates.put(chatId, UserState.ORDER_COMPLETED);
                            sendOrderSuccessMessage(chatId);
                        } else {
                            // Pretend payment failed for testing
                            sendPaymentRetryMessage(chatId);
                        }
                        break;

                    default:
                        handleUnknownCommand(chatId);
                        break;
                }
            }
        }
    }

    private void saveUserSelection(long chatId, String key, String value) {
        userSelections.computeIfAbsent(chatId, k -> new HashMap<>()).put(key, value);
    }

    private String getUserSelection(long chatId, String key) {
        Map<String, String> selections = userSelections.get(chatId);
        return selections != null ? selections.get(key) : null;
    }

    private void removeUserSelection(long chatId, String key) {
        Map<String, String> selections = userSelections.get(chatId);
        if (selections != null) {
            selections.remove(key);
        }
    }

    private void clearUserSelections(long chatId) {
        userSelections.remove(chatId);
    }

    private boolean isValidCoffeeType(String messageText) {
        List<String> validCoffeeTypes = Arrays.asList(
                "Американо", "Латте", "Капучино", "Раф", "Флэт Уайт"
        );
        return validCoffeeTypes.contains(messageText);
    }

    private boolean isValidSize(String messageText) {
        List<String> validSizes = Arrays.asList(
                "Маленький 250 мл", "Средний 350 мл", "Большой 450 мл"
        );
        return validSizes.contains(messageText);
    }

    private boolean isValidMilkType(String messageText) {
        List<String> validMilkTypes = Arrays.asList(
                "Кокосовое", "Миндальное", "Фундучное", "Овсяное"
        );
        return validMilkTypes.contains(messageText);
    }

    private boolean isValidSyrupType(String messageText) {
        List<String> validSyrupTypes = Arrays.asList(
                "Ванильный", "Ореховый", "Карамельный"
        );
        return validSyrupTypes.contains(messageText);
    }

    private void handleAddonsSelection(long chatId, String messageText) {
        switch (messageText) {
            case "Молоко (растительное)":
                userStates.put(chatId, UserState.SELECTING_MILK);
                sendMilkSelectionPage(chatId);
                break;

            case "Сироп (ванильный, ореховый, карамельный)":
                userStates.put(chatId, UserState.SELECTING_SYRUP);
                sendSyrupSelectionPage(chatId);
                break;

            case "Убрать молоко":
                removeUserSelection(chatId, "milkType");
                sendAddonsSelectionPage(chatId);
                break;

            case "Убрать сироп":
                removeUserSelection(chatId, "syrupType");
                sendAddonsSelectionPage(chatId);
                break;

            case "Без добавок":
                // Remove any selections and move to the next step
                removeUserSelection(chatId, "milkType");
                removeUserSelection(chatId, "syrupType");
                userStates.put(chatId, UserState.CONFIRMING_ORDER);
                sendOrderSummary(chatId);
                break;

            case "Готово":
                userStates.put(chatId, UserState.CONFIRMING_ORDER);
                sendOrderSummary(chatId);
                break;

            default:
                handleUnknownCommand(chatId);
                break;
        }
    }

    private void handleOrderConfirmation(long chatId, String messageText) {
        switch (messageText) {
            case "Да":
                // Move to payment flow
                userStates.put(chatId, UserState.PAYMENT_INIT);
                sendPaymentInitPage(chatId);
                break;

            case "Изменить заказ":
                // Go back to add-ons selection page instead of coffee selection
                userStates.put(chatId, UserState.SELECTING_ADDONS);
                sendAddonsSelectionPage(chatId);
                break;

            case "Отмена":
                // Cancel order and restart
                clearUserSelections(chatId);
                userStates.put(chatId, UserState.START);
                sendWelcomeMessage(chatId);
                break;

            default:
                handleUnknownCommand(chatId);
                break;
        }
    }

    private void sendPaymentInitPage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Собрали заказ 👋\nТеперь, чтобы запустить его в работу, нужно произвести оплату.");

        // Create keyboard with payment button
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Оплатить"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Payment init page sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending payment init page to chatId: {}", chatId, e);
        }
    }

    private void sendPaymentConfirmPage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Получилось оплатить?\nКак только завершишь, нажми кнопку ниже.");

        // Create keyboard with payment confirmation button
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Я оплатил(а)"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Payment confirmation page sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending payment confirmation page to chatId: {}", chatId, e);
        }
    }

    private void sendPaymentRetryMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Понимаю, что-то пошло не так с оплатой? Не переживай, такое бывает. Попробуй ещё раз 👋");

        // Keep the same keyboard for retry
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Я оплатил(а)"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Payment retry message sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending payment retry message to chatId: {}", chatId, e);
        }
    }

    private void sendOrderSuccessMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Заказ принят в работу! Мы уведомим вас, когда он будет готов. Спасибо, что выбрали One Shott Coffee! ☕️");

        // Create keyboard to restart
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Новый заказ"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Order success message sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending order success message to chatId: {}", chatId, e);
        }
    }

    private int calculateTotalPrice(long chatId) {
        String coffeeType = getUserSelection(chatId, "coffeeType");
        String size = getUserSelection(chatId, "size");
        String milkType = getUserSelection(chatId, "milkType");
        String syrupType = getUserSelection(chatId, "syrupType");

        int totalPrice = 0;

        // Get size in ml
        int sizeInMl = 0;
        if (size != null) {
            if (size.contains("250")) {
                sizeInMl = 250;
            } else if (size.contains("350")) {
                sizeInMl = 350;
            } else if (size.contains("450")) {
                sizeInMl = 450;
            }
        }

        // Coffee base price
        if (coffeeType != null) {
            switch (coffeeType) {
                case "Американо":
                    if (sizeInMl == 250) totalPrice += 990;
                    else if (sizeInMl == 350) totalPrice += 1090;
                    else if (sizeInMl == 450) totalPrice += 1190;
                    break;
                case "Флэт Уайт":
                    totalPrice += 1090; // Only 250ml
                    break;
                case "Латте":
                case "Капучино":
                    if (sizeInMl == 250) totalPrice += 1090;
                    else if (sizeInMl == 350) totalPrice += 1190;
                    else if (sizeInMl == 450) totalPrice += 1290;
                    break;
                case "Раф":
                    if (sizeInMl == 250) totalPrice += 1290;
                    else if (sizeInMl == 350) totalPrice += 1490;
                    else if (sizeInMl == 450) totalPrice += 1590;
                    break;
            }
        }

        // Add-ons
        if (milkType != null) {
            if (sizeInMl == 250) totalPrice += 350;
            else if (sizeInMl == 350) totalPrice += 450;
            else if (sizeInMl == 450) totalPrice += 550;
        }

        if (syrupType != null) {
            totalPrice += 160;
        }

        return totalPrice;
    }

    private String getSizeLabel(String size) {
        if (size == null) return "";

        if (size.contains("Маленький")) return "Маленький";
        else if (size.contains("Средний")) return "Средний";
        else if (size.contains("Большой")) return "Большой";

        return size;
    }

    private void sendOrderSummary(long chatId) {
        String coffeeType = getUserSelection(chatId, "coffeeType");
        String size = getUserSelection(chatId, "size");
        String milkType = getUserSelection(chatId, "milkType");
        String syrupType = getUserSelection(chatId, "syrupType");
        int totalPrice = calculateTotalPrice(chatId);

        StringBuilder messageText = new StringBuilder("Вот что получилось:\n");
        messageText.append("Напиток: ").append(coffeeType).append("\n");
        messageText.append("Размер: ").append(getSizeLabel(size)).append("\n");

        if (milkType != null) {
            messageText.append("Молоко: ").append(milkType).append("\n");
        }

        if (syrupType != null) {
            messageText.append("Сироп: ").append(syrupType).append("\n");
        }

        messageText.append("Сумма к оплате: ").append(totalPrice).append(" ₸\n\n");
        messageText.append("✅ Всё верно? Подтверждаем?");

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText.toString());

        // Create keyboard with confirmation options
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Да"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Изменить заказ"));
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("Отмена"));
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Order summary sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending order summary to chatId: {}", chatId, e);
        }
    }

    private void sendWelcomeMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Добро пожаловать в One Shott Coffee! ☕️\n" +
                "Здесь можно заказать кофе напрямую - прямо в Telegram!\n" +
                "Нажми «Старт» внизу 👇\n" +
                "1. Выбери кофейню 📍\n" +
                "2. Собери заказ 📝\n" +
                "3. Мы сразу примем его в работу и дадим знать, когда всё будет готово 🔔\n" +
                "Спасибо, что выбираешь нас.\n" +
                "С заботой, Kwonka | 2025");

        // Create keyboard with Start button
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Старт"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Welcome message sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending welcome message to chatId: {}", chatId, e);
        }
    }

    private void sendSecondPage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Привет, я Kwonka! Помогу заказать кофе, чтобы ты не ждал в очереди 🙌");

        // Create keyboard with "Хочу кофе" button
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Хочу кофе"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Second page sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending second page to chatId: {}", chatId, e);
        }
    }

    private void sendCoffeeSelectionPage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Что будем пить сегодня?");

        // Create keyboard with coffee options
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        // Row 1: Американо, Латте
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Американо"));
        row1.add(new KeyboardButton("Латте"));
        keyboard.add(row1);

        // Row 2: Капучино, Раф
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Капучино"));
        row2.add(new KeyboardButton("Раф"));
        keyboard.add(row2);

        // Row 3: Флэт Уайт
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("Флэт Уайт"));
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Coffee selection page sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending coffee selection page to chatId: {}", chatId, e);
        }
    }

    private void sendSizeSelectionPage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Какой размер выберем?");

        // Create keyboard with size options
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        String coffeeType = getUserSelection(chatId, "coffeeType");

        // Флэт Уайт only comes in small size
        if ("Флэт Уайт".equals(coffeeType)) {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton("Маленький 250 мл"));
            keyboard.add(row);
        } else {
            // Add size options as separate rows
            KeyboardRow row1 = new KeyboardRow();
            row1.add(new KeyboardButton("Маленький 250 мл"));
            keyboard.add(row1);

            KeyboardRow row2 = new KeyboardRow();
            row2.add(new KeyboardButton("Средний 350 мл"));
            keyboard.add(row2);

            KeyboardRow row3 = new KeyboardRow();
            row3.add(new KeyboardButton("Большой 450 мл"));
            keyboard.add(row3);
        }

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Size selection page sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending size selection page to chatId: {}", chatId, e);
        }
    }

    private void sendAddonsSelectionPage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        // Create message text with current selections if any
        StringBuilder messageText = new StringBuilder("Хочешь добавить что-нибудь вкусненькое?");

        String milkType = getUserSelection(chatId, "milkType");
        String syrupType = getUserSelection(chatId, "syrupType");

        if (milkType != null || syrupType != null) {
            messageText.append("\n\nТвои текущие добавки:");
            if (milkType != null) {
                messageText.append("\n- Молоко: ").append(milkType);
            }
            if (syrupType != null) {
                messageText.append("\n- Сироп: ").append(syrupType);
            }
        }

        message.setText(messageText.toString());

        // Create keyboard with add-on options
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Молоко (растительное)"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Сироп (ванильный, ореховый, карамельный)"));
        keyboard.add(row2);

        // Add buttons to remove selections if they exist
        if (milkType != null) {
            KeyboardRow rowMilk = new KeyboardRow();
            rowMilk.add(new KeyboardButton("Убрать молоко"));
            keyboard.add(rowMilk);
        }

        if (syrupType != null) {
            KeyboardRow rowSyrup = new KeyboardRow();
            rowSyrup.add(new KeyboardButton("Убрать сироп"));
            keyboard.add(rowSyrup);
        }

        if (milkType != null || syrupType != null) {
            KeyboardRow rowNext = new KeyboardRow();
            rowNext.add(new KeyboardButton("Готово"));
            keyboard.add(rowNext);
        }

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("Без добавок"));
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Add-ons selection page sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending add-ons selection page to chatId: {}", chatId, e);
        }
    }

    private void sendMilkSelectionPage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выберите тип растительного молока:");

        // Create keyboard with milk options
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Кокосовое"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Миндальное"));
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("Фундучное"));
        keyboard.add(row3);

        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton("Овсяное"));
        keyboard.add(row4);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Milk selection page sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending milk selection page to chatId: {}", chatId, e);
        }
    }

    private void sendSyrupSelectionPage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выберите сироп:");

        // Create keyboard with syrup options
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Ванильный"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Ореховый"));
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("Карамельный"));
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.debug("Syrup selection page sent to chatId: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending syrup selection page to chatId: {}", chatId, e);
        }
    }

    private void handleUnknownCommand(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Извините, я не понимаю эту команду. Пожалуйста, воспользуйтесь предложенными кнопками или отправьте /start, чтобы начать заново.");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending unknown command message to chatId: {}", chatId, e);
        }
    }

    // Enum to track user state in the conversation
    private enum UserState {
        START,
        INTRO,
        SELECTING_COFFEE,
        SELECTING_SIZE,
        SELECTING_ADDONS,
        SELECTING_MILK,
        SELECTING_SYRUP,
        CONFIRMING_ORDER,
        PAYMENT_INIT,
        PAYMENT_CONFIRM,
        ORDER_COMPLETED
    }
}