package com.kwonka.common.service;

import com.kwonka.common.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service to coordinate communication between bots
 * This is a simplified implementation for the demo
 * In a real application, you might use a message broker like RabbitMQ or Kafka
 */
@Service
@Slf4j
public class BotCommunicationService {

    private final ApplicationEventPublisher eventPublisher;
    private final Map<Long, Consumer<Order>> customerCallbacks = new HashMap<>();
    private final Map<String, Consumer<Order>> baristaCallbacks = new HashMap<>();

    public BotCommunicationService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Register a callback for a customer
     */
    public void registerCustomerCallback(Long customerId, Consumer<Order> callback) {
        customerCallbacks.put(customerId, callback);
        log.debug("Registered customer callback for customerId: {}", customerId);
    }

    /**
     * Register a callback for a barista location
     */
    public void registerBaristaCallback(String locationCode, Consumer<Order> callback) {
        baristaCallbacks.put(locationCode, callback);
        log.debug("Registered barista callback for location: {}", locationCode);
    }

    /**
     * Notify customer about order status change
     */
    public void notifyCustomer(Order order) {
        Consumer<Order> callback = customerCallbacks.get(order.getCustomerId());
        if (callback != null) {
            callback.accept(order);
            log.debug("Notified customer {} about order status change: {}", order.getCustomerId(), order.getStatus());
        } else {
            log.debug("No callback registered for customer: {}", order.getCustomerId());
        }
    }

    /**
     * Notify barista about new order
     */
    public void notifyBarista(Order order) {
        String locationCode = order.getCoffeeShop().getCode();
        Consumer<Order> callback = baristaCallbacks.get(locationCode);
        if (callback != null) {
            callback.accept(order);
            log.debug("Notified barista at location {} about new order", locationCode);
        } else {
            log.debug("No callback registered for location: {}", locationCode);
        }
    }

    /**
     * Listen for order status changes and notify relevant parties
     */
    @Async
    @EventListener
    public void handleOrderStatusChange(OrderStatusChangeEvent event) {
        Order order = event.getOrder();
        notifyCustomer(order);
        notifyBarista(order);
    }

    /**
     * Event class for order status changes
     */
    public static class OrderStatusChangeEvent {
        private final Order order;

        public OrderStatusChangeEvent(Order order) {
            this.order = order;
        }

        public Order getOrder() {
            return order;
        }
    }
}