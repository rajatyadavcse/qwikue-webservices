package com.restaurant.service.service;

import com.restaurant.service.dao.OrderEntityDAO;
import com.restaurant.service.dao.OrderEntityId;
import com.restaurant.service.exception.ResourceNotFoundException;
import com.restaurant.service.mapper.OrderEntityMapper;
import com.restaurant.service.model.OrderEntity;
import com.restaurant.service.model.OrderEntityStatus;
import com.restaurant.service.model.OrderEntityType;
import com.restaurant.service.repository.OrderEntityRepository;
import com.restaurant.service.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderEntityServiceImplTest {

    @Mock
    private OrderEntityRepository orderEntityRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private OrderEntityMapper orderEntityMapper;

    @InjectMocks
    private OrderEntityServiceImpl orderEntityService;

    private OrderEntity sampleEntity;
    private OrderEntityDAO sampleDAO;

    @BeforeEach
    void setUp() {
        sampleEntity = new OrderEntity();
        sampleEntity.setEntityNo("Table-1");
        sampleEntity.setRestaurantId(1L);
        sampleEntity.setOrderEntityType(OrderEntityType.TABLE);

        sampleDAO = new OrderEntityDAO();
        OrderEntityId id = new OrderEntityId();
        id.setEntityNo("Table-1");
        id.setRestaurantId(1L);
        sampleDAO.setOrderEntityId(id);
        sampleDAO.setOrderEntityType(OrderEntityType.TABLE);
        sampleDAO.setStatus("AVAILABLE");
    }

    @Test
    void createOrderEntity_defaultsStatusToAvailableWhenNull() {
        when(restaurantRepository.existsById(1L)).thenReturn(true);
        when(orderEntityRepository.existsById(any(OrderEntityId.class))).thenReturn(false);
        when(orderEntityMapper.orderEntityToOrderEntityDAO(sampleEntity)).thenReturn(sampleDAO);
        when(orderEntityRepository.save(sampleDAO)).thenReturn(sampleDAO);
        when(orderEntityMapper.orderEntityDAOToOrderEntity(sampleDAO)).thenReturn(sampleEntity);

        OrderEntity created = orderEntityService.createOrderEntity(sampleEntity);

        assertEquals("AVAILABLE", sampleEntity.getStatus());
        assertNotNull(created);
    }

    @Test
    void createOrderEntity_throwsExceptionForInvalidStatus() {
        when(restaurantRepository.existsById(1L)).thenReturn(true);
        when(orderEntityRepository.existsById(any(OrderEntityId.class))).thenReturn(false);
        sampleEntity.setStatus("INVALID_STATUS");

        assertThrows(IllegalArgumentException.class, () -> orderEntityService.createOrderEntity(sampleEntity));
    }

    @Test
    void updateOrderEntityStatus_success() {
        OrderEntityId id = new OrderEntityId();
        id.setEntityNo("Table-1");
        id.setRestaurantId(1L);

        when(orderEntityRepository.findById(id)).thenReturn(Optional.of(sampleDAO));
        when(orderEntityRepository.save(sampleDAO)).thenReturn(sampleDAO);
        when(orderEntityMapper.orderEntityDAOToOrderEntity(sampleDAO)).thenReturn(sampleEntity);

        OrderEntity updated = orderEntityService.updateOrderEntityStatus("Table-1", 1L, OrderEntityStatus.OCCUPIED);

        assertEquals("OCCUPIED", sampleDAO.getStatus());
        assertNotNull(updated);
        verify(orderEntityRepository).save(sampleDAO);
    }

    @Test
    void updateOrderEntityStatus_notFound_throwsException() {
        OrderEntityId id = new OrderEntityId();
        id.setEntityNo("Table-99");
        id.setRestaurantId(1L);

        when(orderEntityRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                orderEntityService.updateOrderEntityStatus("Table-99", 1L, OrderEntityStatus.OCCUPIED));
    }
}
