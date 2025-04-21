package com.kwonka.common.service;

import com.kwonka.common.entity.CoffeeShop;
import com.kwonka.common.repository.CoffeeShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CoffeeShopService {

    private final CoffeeShopRepository coffeeShopRepository;

    /**
     * Get all active coffee shops
     */
    public List<CoffeeShop> getAllActiveShops() {
        return coffeeShopRepository.findByActiveTrue();
    }

    /**
     * Get a coffee shop by its code
     */
    public Optional<CoffeeShop> getShopByCode(String code) {
        return coffeeShopRepository.findByCode(code);
    }

    /**
     * Get a coffee shop by its ID
     */
    public Optional<CoffeeShop> findById(Long id) {
        return coffeeShopRepository.findById(id);
    }

    /**
     * Create a new coffee shop
     */
    @Transactional
    public CoffeeShop createCoffeeShop(String name, String address, String code) {
        CoffeeShop coffeeShop = CoffeeShop.builder()
                .name(name)
                .address(address)
                .code(code)
                .active(true)
                .build();

        return coffeeShopRepository.save(coffeeShop);
    }

    /**
     * Update a coffee shop's details
     */
    @Transactional
    public Optional<CoffeeShop> updateCoffeeShop(Long id, String name, String address, String code, Boolean active) {
        Optional<CoffeeShop> coffeeShopOpt = coffeeShopRepository.findById(id);
        if (coffeeShopOpt.isPresent()) {
            CoffeeShop coffeeShop = coffeeShopOpt.get();

            if (name != null) {
                coffeeShop.setName(name);
            }

            if (address != null) {
                coffeeShop.setAddress(address);
            }

            if (code != null) {
                coffeeShop.setCode(code);
            }

            if (active != null) {
                coffeeShop.setActive(active);
            }

            return Optional.of(coffeeShopRepository.save(coffeeShop));
        }

        return Optional.empty();
    }

    /**
     * Deactivate a coffee shop
     */
    @Transactional
    public Optional<CoffeeShop> deactivateShop(Long id) {
        Optional<CoffeeShop> coffeeShopOpt = coffeeShopRepository.findById(id);
        if (coffeeShopOpt.isPresent()) {
            CoffeeShop coffeeShop = coffeeShopOpt.get();
            coffeeShop.setActive(false);
            return Optional.of(coffeeShopRepository.save(coffeeShop));
        }

        return Optional.empty();
    }
}