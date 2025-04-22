package com.kwonka.common.service;

import com.kwonka.common.entity.CoffeeShop;
import com.kwonka.common.entity.Order;
import com.kwonka.common.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * Creates a new order with PENDING status
     */
    @Transactional
    public Order createOrder(Long customerId, CoffeeShop coffeeShop, String coffeeType, String size,
                             String milkType, String syrupType, BigDecimal totalPrice) {

        String orderNumber = generateOrderNumber();

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .customerId(customerId)
                .coffeeShop(coffeeShop)
                .coffeeType(coffeeType)
                .size(size)
                .milkType(milkType)
                .syrupType(syrupType)
                .totalPrice(totalPrice)
                .status(Order.OrderStatus.PENDING)
                .build();

        return orderRepository.save(order);
    }

    /**
     * Generates a sequential order number
     */
    private String generateOrderNumber() {
        Integer maxOrderNumber = orderRepository.findMaxOrderNumber();
        if (maxOrderNumber == null) {
            return "1";
        }
        return String.valueOf(maxOrderNumber + 1);
    }

    /**
     * Updates the order status
     */
    @Transactional
    public Order updateOrderStatus(String orderNumber, Order.OrderStatus newStatus) {
        Optional<Order> orderOpt = orderRepository.findByOrderNumber(orderNumber);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setStatus(newStatus);
            return orderRepository.save(order);
        }
        throw new RuntimeException("Order not found: " + orderNumber);
    }

    /**
     * Gets an order by its number
     */
    public Optional<Order> getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    /**
     * Gets all orders for a customer
     */
    public List<Order> getOrdersByCustomerId(Long customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    /**
     * Gets all orders with a specific status
     */
    public List<Order> getOrdersByStatus(Order.OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    /**
     * Gets all orders for a specific coffee shop with a specific status
     */
    public List<Order> getOrdersByShopAndStatus(CoffeeShop coffeeShop, Order.OrderStatus status) {
        return orderRepository.findByCoffeeShopAndStatus(coffeeShop, status);
    }

    /**
     * Gets all orders for a specific customer with a specific status
     */
    public List<Order> getOrdersByCustomerIdAndStatus(Long customerId, Order.OrderStatus status) {
        return orderRepository.findByCustomerIdAndStatus(customerId, status);
    }
}