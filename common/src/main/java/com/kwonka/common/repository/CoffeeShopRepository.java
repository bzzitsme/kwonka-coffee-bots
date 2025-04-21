package com.kwonka.common.repository;

import com.kwonka.common.entity.CoffeeShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoffeeShopRepository extends JpaRepository<CoffeeShop, Long> {

    Optional<CoffeeShop> findByCode(String code);

    List<CoffeeShop> findByActiveTrue();
}