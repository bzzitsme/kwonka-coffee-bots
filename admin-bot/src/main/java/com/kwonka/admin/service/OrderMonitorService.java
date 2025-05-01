package com.kwonka.admin.service;

import com.kwonka.common.entity.Order;
import com.kwonka.common.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for monitoring orders that have been pending for too long
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderMonitorService {

    private final OrderService orderService;

    // Map to store admin chat IDs that are monitoring pending orders
    private final Map<Long, Boolean> adminMonitors = new HashMap<>();

    // Map to store notifications already sent to admins (to avoid spamming)
    private final Map<String, Boolean> notifiedOrders = new HashMap<>();

    /**
     * Register an admin to receive notifications about pending orders
     *
     * @param adminChatId Admin's Telegram chat ID
     */
    public void registerAdminMonitor(Long adminChatId) {
        adminMonitors.put(adminChatId, true);
        log.info("Admin {} registered for order monitoring", adminChatId);
    }

    /**
     * Unregister an admin from receiving notifications
     *
     * @param adminChatId Admin's Telegram chat ID
     */
    public void unregisterAdminMonitor(Long adminChatId) {
        adminMonitors.remove(adminChatId);
        log.info("Admin {} unregistered from order monitoring", adminChatId);
    }

    /**
     * Get all admin chat IDs that are monitoring pending orders
     *
     * @return List of admin chat IDs
     */
    public List<Long> getActiveAdminMonitors() {
        return new ArrayList<>(adminMonitors.keySet());
    }

    /**
     * Get all pending orders that have been waiting for over 5 minutes
     *
     * @return List of orders with wait time in minutes
     */
    public List<Map.Entry<Order, Integer>> getDelayedPendingOrders() {
        List<Order> pendingOrders = orderService.getOrdersByStatus(Order.OrderStatus.PENDING);
        List<Map.Entry<Order, Integer>> delayedOrders = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

        for (Order order : pendingOrders) {
            LocalDateTime createdAt = order.getCreatedAt();
            int waitTimeMinutes = (int) Duration.between(createdAt, now).toMinutes();

            if (waitTimeMinutes >= 5) {
                delayedOrders.add(Map.entry(order, waitTimeMinutes));
            }
        }

        return delayedOrders;
    }

    /**
     * Check if an order has been notified to admins already
     *
     * @param orderNumber Order number
     * @return True if already notified
     */
    public boolean isOrderAlreadyNotified(String orderNumber) {
        return notifiedOrders.containsKey(orderNumber);
    }

    /**
     * Mark an order as notified to admins
     *
     * @param orderNumber Order number
     */
    public void markOrderAsNotified(String orderNumber) {
        notifiedOrders.put(orderNumber, true);
    }

    /**
     * Clear notification status for an order (e.g., when it's taken by a barista)
     *
     * @param orderNumber Order number
     */
    public void clearOrderNotification(String orderNumber) {
        notifiedOrders.remove(orderNumber);
    }

    /**
     * Scheduled task to clean up very old notifications (over 1 hour)
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupOldNotifications() {
        List<Order> completedOrders = orderService.getOrdersByStatus(Order.OrderStatus.COMPLETED);
        List<Order> inProgressOrders = orderService.getOrdersByStatus(Order.OrderStatus.IN_PREPARATION);
        List<Order> readyOrders = orderService.getOrdersByStatus(Order.OrderStatus.READY);

        // Clean up notification status for orders that are no longer pending
        for (Order order : completedOrders) {
            clearOrderNotification(order.getOrderNumber());
        }

        for (Order order : inProgressOrders) {
            clearOrderNotification(order.getOrderNumber());
        }

        for (Order order : readyOrders) {
            clearOrderNotification(order.getOrderNumber());
        }

        log.info("Cleaned up order notifications");
    }
}