package com.restaurant.service.service;

import com.restaurant.service.dao.RestaurantDAO;
import com.restaurant.service.exception.ResourceNotFoundException;
import com.restaurant.service.mapper.RestaurantMapper;
import com.restaurant.service.model.Restaurant;
import com.restaurant.service.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceImplTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantMapper mapper;

    @InjectMocks
    private RestaurantServiceImpl restaurantService;

    private Restaurant sampleRestaurant;
    private RestaurantDAO sampleRestaurantDAO;

    @BeforeEach
    void setUp() {
        sampleRestaurant = new Restaurant();
        sampleRestaurant.setRestaurantId(1L);
        sampleRestaurant.setRestaurantName("Test Restaurant");
        sampleRestaurant.setUpiId("test@upi");

        sampleRestaurantDAO = new RestaurantDAO();
        sampleRestaurantDAO.setRestaurantId(1L);
        sampleRestaurantDAO.setRestaurantName("Test Restaurant");
        sampleRestaurantDAO.setUpiId("test@upi");
    }

    @Test
    @DisplayName("createRestaurant - successfully sets upiId and returns saved restaurant")
    void createRestaurant_savesAndReturnsUpiId() {
        when(mapper.restaurantToRestaurantDAO(any(Restaurant.class))).thenReturn(sampleRestaurantDAO);
        when(restaurantRepository.save(any(RestaurantDAO.class))).thenReturn(sampleRestaurantDAO);
        when(mapper.restaurantDAOToRestaurant(any(RestaurantDAO.class))).thenReturn(sampleRestaurant);

        Restaurant created = restaurantService.createRestaurant(sampleRestaurant);

        assertNotNull(created);
        assertEquals("test@upi", created.getUpiId());
        verify(restaurantRepository, times(1)).save(any(RestaurantDAO.class));
    }

    @Test
    @DisplayName("getRestaurantById - returns restaurant with upiId")
    void getRestaurantById_returnsRestaurantWithUpiId() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(sampleRestaurantDAO));
        when(mapper.restaurantDAOToRestaurant(sampleRestaurantDAO)).thenReturn(sampleRestaurant);

        Restaurant found = restaurantService.getRestaurantById(1L);

        assertNotNull(found);
        assertEquals("test@upi", found.getUpiId());
    }

    @Test
    @DisplayName("getRestaurantById - throws ResourceNotFoundException when not found")
    void getRestaurantById_notFound_throwsException() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> restaurantService.getRestaurantById(99L));
    }

    @Test
    @DisplayName("updateRestaurant - successfully updates upiId")
    void updateRestaurant_updatesUpiId() {
        Restaurant updatePayload = new Restaurant();
        updatePayload.setRestaurantName("Updated Restaurant");
        updatePayload.setUpiId("updated@okicici");

        RestaurantDAO updatedDAO = new RestaurantDAO();
        updatedDAO.setRestaurantId(1L);
        updatedDAO.setRestaurantName("Updated Restaurant");
        updatedDAO.setUpiId("updated@okicici");

        Restaurant updatedModel = new Restaurant();
        updatedModel.setRestaurantId(1L);
        updatedModel.setRestaurantName("Updated Restaurant");
        updatedModel.setUpiId("updated@okicici");

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(sampleRestaurantDAO));
        when(restaurantRepository.save(any(RestaurantDAO.class))).thenReturn(updatedDAO);
        when(mapper.restaurantDAOToRestaurant(updatedDAO)).thenReturn(updatedModel);

        Restaurant result = restaurantService.updateRestaurant(1L, updatePayload);

        assertNotNull(result);
        assertEquals("updated@okicici", result.getUpiId());
        assertEquals("updated@okicici", sampleRestaurantDAO.getUpiId());
        verify(restaurantRepository, times(1)).save(sampleRestaurantDAO);
    }
}
