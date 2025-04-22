package com.kwonka.common.repository;

import com.kwonka.common.entity.CoffeeShop;
import com.kwonka.common.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByCustomerId(Long customerId);

    List<Order> findByStatus(Order.OrderStatus status);

    List<Order> findByCoffeeShopAndStatus(CoffeeShop coffeeShop, Order.OrderStatus status);

    List<Order> findByCustomerIdAndStatus(Long customerId, Order.OrderStatus status);

    List<Order> findByCoffeeShop(CoffeeShop coffeeShop);

    @Query("SELECT MAX(CAST(o.orderNumber AS int)) FROM Order o")
    Integer findMaxOrderNumber();
}