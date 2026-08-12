package com.restaurant.service.service;

import com.restaurant.service.dao.OrderEntityDAO;
import com.restaurant.service.dao.OrderEntityId;
import com.restaurant.service.exception.ResourceNotFoundException;
import com.restaurant.service.mapper.OrderEntityMapper;
import com.restaurant.service.event.OrderEntityUpdateEvent;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        verify(eventPublisher).publishEvent(any(OrderEntityUpdateEvent.class));
    }

    @Test
    void createOrderEntity_throwsExceptionForInvalidStatus() {
        when(restaurantRepository.existsById(1L)).thenReturn(true);
        when(orderEntityRepository.existsById(any(OrderEntityId.class))).thenReturn(false);
        sampleEntity.setStatus("INVALID_STATUS");

        assertThrows(IllegalArgumentException.class, () -> orderEntityService.createOrderEntity(sampleEntity));
    }

    @Test
    void updateOrderEntity_success() {
        OrderEntityId id = new OrderEntityId();
        id.setEntityNo("Table-1");
        id.setRestaurantId(1L);

        when(orderEntityRepository.existsById(id)).thenReturn(true);
        when(orderEntityMapper.orderEntityToOrderEntityDAO(sampleEntity)).thenReturn(sampleDAO);
        when(orderEntityRepository.save(sampleDAO)).thenReturn(sampleDAO);
        when(orderEntityMapper.orderEntityDAOToOrderEntity(sampleDAO)).thenReturn(sampleEntity);

        OrderEntity updated = orderEntityService.updateOrderEntity(sampleEntity);

        assertNotNull(updated);
        verify(eventPublisher).publishEvent(any(OrderEntityUpdateEvent.class));
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
        verify(eventPublisher).publishEvent(any(OrderEntityUpdateEvent.class));
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

    @Test
    void getOrderEntitiesByRestaurantId_numericEntityNo_sortsAscending() {
        OrderEntityDAO dao1 = new OrderEntityDAO();
        OrderEntityDAO dao2 = new OrderEntityDAO();
        OrderEntityDAO dao3 = new OrderEntityDAO();

        OrderEntity e1 = new OrderEntity();
        e1.setEntityNo("10");
        OrderEntity e2 = new OrderEntity();
        e2.setEntityNo("2");
        OrderEntity e3 = new OrderEntity();
        e3.setEntityNo("1");

        List<OrderEntityDAO> daos = Arrays.asList(dao1, dao2, dao3);
        List<OrderEntity> mappedEntities = new ArrayList<>(Arrays.asList(e1, e2, e3));

        when(orderEntityRepository.findAllByOrderEntityIdRestaurantId(1L)).thenReturn(daos);
        when(orderEntityMapper.orderEntityDAOListToOrderEntityList(daos)).thenReturn(mappedEntities);

        List<OrderEntity> result = orderEntityService.getOrderEntitiesByRestaurantId(1L);

        assertEquals(3, result.size());
        assertEquals("1", result.get(0).getEntityNo());
        assertEquals("2", result.get(1).getEntityNo());
        assertEquals("10", result.get(2).getEntityNo());
    }

    @Test
    void getOrderEntitiesByRestaurantId_nonNumericEntityNo_skipsSorting() {
        OrderEntityDAO dao1 = new OrderEntityDAO();
        OrderEntityDAO dao2 = new OrderEntityDAO();

        OrderEntity e1 = new OrderEntity();
        e1.setEntityNo("Table-10");
        OrderEntity e2 = new OrderEntity();
        e2.setEntityNo("Table-2");

        List<OrderEntityDAO> daos = Arrays.asList(dao1, dao2);
        List<OrderEntity> mappedEntities = new ArrayList<>(Arrays.asList(e1, e2));

        when(orderEntityRepository.findAllByOrderEntityIdRestaurantId(1L)).thenReturn(daos);
        when(orderEntityMapper.orderEntityDAOListToOrderEntityList(daos)).thenReturn(mappedEntities);

        List<OrderEntity> result = orderEntityService.getOrderEntitiesByRestaurantId(1L);

        assertEquals(2, result.size());
        assertEquals("Table-10", result.get(0).getEntityNo());
        assertEquals("Table-2", result.get(1).getEntityNo());
    }

    @Test
    void getOrderEntitiesByRestaurantId_emptyList_returnsEmptyList() {
        when(orderEntityRepository.findAllByOrderEntityIdRestaurantId(1L)).thenReturn(new ArrayList<>());

        List<OrderEntity> result = orderEntityService.getOrderEntitiesByRestaurantId(1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getOrderEntitiesByRestaurantId_nullRestaurantId_throwsException() {
        assertThrows(ResourceNotFoundException.class, () ->
                orderEntityService.getOrderEntitiesByRestaurantId(null));
    }
}
