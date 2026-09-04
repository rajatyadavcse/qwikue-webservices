package com.restaurant.service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import com.restaurant.service.dao.RestaurantDAO;
import com.restaurant.service.exception.ResourceNotFoundException;
import com.restaurant.service.mapper.RestaurantMapper;
import com.restaurant.service.model.Restaurant;
import com.restaurant.service.repository.RestaurantRepository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RestaurantServiceImpl implements IRestaurantService {

    @Autowired
    RestaurantRepository restaurantRepository;

    @Autowired
    RestaurantMapper mapper;

    @Override
    @CacheEvict(value = "restaurantService:allRestaurants", allEntries = true)
    public Restaurant createRestaurant(Restaurant restaurant) {
        restaurant.setRestaurantId(null); // ensure Hibernate treats this as a new entity
        if (restaurant.getPaymentModes() == null || restaurant.getPaymentModes().isEmpty()) {
            restaurant.setPaymentModes(List.of(com.restaurant.service.model.PaymentMode.CASH));
        }
        if (restaurant.getTipEnabled() == null) {
            restaurant.setTipEnabled(false);
        }
        if (restaurant.getCreatedDate() == null) {
            restaurant.setCreatedDate(new Date());
        }
        restaurant.setUpdatedDate(new Date());
        return mapper
                .restaurantDAOToRestaurant(restaurantRepository.save(mapper.restaurantToRestaurantDAO(restaurant)));
    }

    @Override
    @Cacheable(value = "restaurantService:allRestaurants")
    public List<Restaurant> getAllRestaurants() {
        return restaurantRepository.findAll().stream()
                .map(mapper::restaurantDAOToRestaurant)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "restaurantService:restaurants", key = "#id")
    public Restaurant getRestaurantById(Long id) {
        RestaurantDAO restaurantDAO = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id: " + id));
        return mapper.restaurantDAOToRestaurant(restaurantDAO);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "restaurantService:restaurants", key = "#id"),
            @CacheEvict(value = "restaurantService:allRestaurants", allEntries = true)
    })
    public Restaurant updateRestaurant(Long id, Restaurant restaurantDetails) {
        RestaurantDAO existingRestaurantDAO = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id: " + id));

        // Update fields
        existingRestaurantDAO.setRestaurantName(restaurantDetails.getRestaurantName());
        existingRestaurantDAO.setGstin(restaurantDetails.getGstin());
        existingRestaurantDAO.setAddress(restaurantDetails.getAddress());
        existingRestaurantDAO.setPhoneNo(restaurantDetails.getPhoneNo());
        existingRestaurantDAO.setStatus(restaurantDetails.getStatus());
        existingRestaurantDAO.setEmail(restaurantDetails.getEmail());
        existingRestaurantDAO.setType(restaurantDetails.getType());
        existingRestaurantDAO.setEstablishmentType(restaurantDetails.getEstablishmentType());
        existingRestaurantDAO.setOrderEntityTypes(restaurantDetails.getOrderEntityTypes());
        existingRestaurantDAO.setTaxesAndCharges(restaurantDetails.getTaxesAndCharges());
        existingRestaurantDAO.setPaymentModes(restaurantDetails.getPaymentModes());
        if (restaurantDetails.getRazorpayKeyId() != null && !restaurantDetails.getRazorpayKeyId().isBlank() &&
                restaurantDetails.getRazorpayKeySecret() != null
                && !restaurantDetails.getRazorpayKeySecret().isBlank()) {
            existingRestaurantDAO.setRazorpayKeyId(restaurantDetails.getRazorpayKeyId());
            existingRestaurantDAO.setRazorpayKeySecret(restaurantDetails.getRazorpayKeySecret());
        } else {
            restaurantDetails.setRazorpayKeyId(existingRestaurantDAO.getRazorpayKeyId());
            restaurantDetails.setRazorpayKeySecret(existingRestaurantDAO.getRazorpayKeySecret());
            existingRestaurantDAO.setRazorpayKeyId(restaurantDetails.getRazorpayKeyId());
            existingRestaurantDAO.setRazorpayKeySecret(restaurantDetails.getRazorpayKeySecret());
        }
        existingRestaurantDAO.setUpiId(restaurantDetails.getUpiId());
        existingRestaurantDAO.setTipEnabled(restaurantDetails.getTipEnabled() != null ? restaurantDetails.getTipEnabled() : false);
        existingRestaurantDAO.setUpdatedDate(new Date());

        return mapper.restaurantDAOToRestaurant(restaurantRepository.save(existingRestaurantDAO));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "restaurantService:restaurants", key = "#id"),
            @CacheEvict(value = "restaurantService:allRestaurants", allEntries = true)
    })
    public void deleteRestaurant(Long id) {
        RestaurantDAO existingRestaurantDAO = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id: " + id));

        restaurantRepository.delete(existingRestaurantDAO);
    }
}
