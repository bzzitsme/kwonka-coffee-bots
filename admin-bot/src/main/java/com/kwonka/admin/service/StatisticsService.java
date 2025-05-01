package com.kwonka.admin.service;

import com.kwonka.common.entity.CoffeeShop;
import com.kwonka.common.entity.Order;
import com.kwonka.common.service.CoffeeShopService;
import com.kwonka.common.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating statistics about orders
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsService {

    private final OrderService orderService;
    private final CoffeeShopService coffeeShopService;

    /**
     * Statistics for a single coffee shop
     */
    public static class CoffeeShopStats {
        private final String name;
        private int orderCount;
        private BigDecimal totalRevenue;

        public CoffeeShopStats(String name) {
            this.name = name;
            this.orderCount = 0;
            this.totalRevenue = BigDecimal.ZERO;
        }

        public void addOrder(Order order) {
            orderCount++;
            totalRevenue = totalRevenue.add(order.getTotalPrice());
        }

        public String getName() {
            return name;
        }

        public int getOrderCount() {
            return orderCount;
        }

        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }
    }

    /**
     * Daily statistics across all coffee shops
     */
    public static class DailyStats {
        private final LocalDate date;
        private final Map<String, CoffeeShopStats> coffeeShopStats;
        private int totalOrderCount;
        private BigDecimal totalRevenue;

        public DailyStats(LocalDate date) {
            this.date = date;
            this.coffeeShopStats = new HashMap<>();
            this.totalOrderCount = 0;
            this.totalRevenue = BigDecimal.ZERO;
        }

        public void addShopStats(CoffeeShopStats shopStats) {
            coffeeShopStats.put(shopStats.getName(), shopStats);
            totalOrderCount += shopStats.getOrderCount();
            totalRevenue = totalRevenue.add(shopStats.getTotalRevenue());
        }

        public LocalDate getDate() {
            return date;
        }

        public Map<String, CoffeeShopStats> getCoffeeShopStats() {
            return coffeeShopStats;
        }

        public int getTotalOrderCount() {
            return totalOrderCount;
        }

        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }
    }

    /**
     * Get statistics for the current day
     *
     * @return Daily statistics
     */
    public DailyStats getCurrentDayStats() {
        return getDayStats(LocalDate.now());
    }

    /**
     * Get statistics for a specific day
     *
     * @param date The date to get statistics for
     * @return Daily statistics
     */
    public DailyStats getDayStats(LocalDate date) {
        // Define day start and end
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

        // Get all completed orders for the day
        List<Order> completedOrders = orderService.getOrdersByStatus(Order.OrderStatus.COMPLETED).stream()
                .filter(order -> !order.getCreatedAt().isBefore(dayStart) && !order.getCreatedAt().isAfter(dayEnd))
                .collect(Collectors.toList());

        // Get all coffee shops
        List<CoffeeShop> coffeeShops = coffeeShopService.getAllActiveShops();

        // Create daily stats
        DailyStats dailyStats = new DailyStats(date);

        // Calculate stats for each coffee shop
        for (CoffeeShop shop : coffeeShops) {
            CoffeeShopStats shopStats = new CoffeeShopStats(shop.getName());

            // Find orders for this shop
            List<Order> shopOrders = completedOrders.stream()
                    .filter(order -> order.getCoffeeShop().getId().equals(shop.getId()))
                    .collect(Collectors.toList());

            // Add each order to the shop's stats
            for (Order order : shopOrders) {
                shopStats.addOrder(order);
            }

            // Add the shop's stats to the daily stats
            dailyStats.addShopStats(shopStats);
        }

        return dailyStats;
    }

    /**
     * Format daily statistics as a text message
     *
     * @param stats Daily statistics
     * @return Formatted message
     */
    public String formatDailyStats(DailyStats stats) {
        StringBuilder message = new StringBuilder();

        message.append("📊 *Статистика заказов*\n\n");
        message.append("📅 Дата: ").append(stats.getDate()).append("\n");
        message.append("🧮 Количество заказов: ").append(stats.getTotalOrderCount()).append("\n");
        message.append("💰 Общий доход: ").append(stats.getTotalRevenue()).append(" ₸\n\n");

        message.append("*По кофейням:*\n");

        for (CoffeeShopStats shopStats : stats.getCoffeeShopStats().values()) {
            message.append("📍 ").append(shopStats.getName()).append(":\n");
            message.append("   📝 Заказов: ").append(shopStats.getOrderCount()).append("\n");
            message.append("   💵 Доход: ").append(shopStats.getTotalRevenue()).append(" ₸\n\n");
        }

        return message.toString();
    }
}