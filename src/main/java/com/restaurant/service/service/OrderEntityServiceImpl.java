package com.restaurant.service.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.restaurant.service.dao.OrderEntityDAO;
import com.restaurant.service.dao.OrderEntityId;
import com.restaurant.service.exception.ResourceAlreadyExistsException;
import com.restaurant.service.exception.ResourceNotFoundException;
import com.restaurant.service.mapper.OrderEntityMapper;
import com.restaurant.service.model.OrderEntity;
import com.restaurant.service.repository.RestaurantRepository;
import com.restaurant.service.repository.OrderEntityRepository;

@Service
public class OrderEntityServiceImpl implements IOrderEntityService {

    @Autowired
    OrderEntityRepository orderEntityRepository;

    @Autowired
    RestaurantRepository restaurantRepository;

    @Autowired
    OrderEntityMapper orderEntityMapper;

    @Override
    public OrderEntity createOrderEntity(OrderEntity orderEntity) {
        if (orderEntity.getEntityNo() == null || orderEntity.getRestaurantId() == null) {
            throw new ResourceNotFoundException("Entity No and Restaurant ID are required for creating an order entity");
        }

        if (!restaurantRepository.existsById(orderEntity.getRestaurantId())) {
            throw new ResourceNotFoundException("Restaurant with id " + orderEntity.getRestaurantId() + " not found");
        }

        OrderEntityId orderEntityId = new OrderEntityId();
        orderEntityId.setEntityNo(orderEntity.getEntityNo());
        orderEntityId.setRestaurantId(orderEntity.getRestaurantId());
        
        if (orderEntityRepository.existsById(orderEntityId)) {
            throw new ResourceAlreadyExistsException("A record with entityNo " + orderEntity.getEntityNo()
                    + " and restaurantId " + orderEntity.getRestaurantId()
                    + " already exists. Please try with different entityNo or try updating the existing record");
        }
        
        OrderEntityDAO orderEntityDAO = orderEntityMapper.orderEntityToOrderEntityDAO(orderEntity);
        return orderEntityMapper.orderEntityDAOToOrderEntity(orderEntityRepository.save(orderEntityDAO));
    }

    @Override
    public OrderEntity updateOrderEntity(OrderEntity orderEntity) {
        OrderEntityId orderEntityId = new OrderEntityId();
        orderEntityId.setEntityNo(orderEntity.getEntityNo());
        orderEntityId.setRestaurantId(orderEntity.getRestaurantId());
        
        if (orderEntity.getEntityNo() == null || orderEntity.getRestaurantId() == null || !orderEntityRepository.existsById(orderEntityId)) {
            throw new ResourceNotFoundException("Order Entity ID is required for update");
        }

        OrderEntityDAO orderEntityDAO = orderEntityMapper.orderEntityToOrderEntityDAO(orderEntity);
        return orderEntityMapper.orderEntityDAOToOrderEntity(orderEntityRepository.save(orderEntityDAO));
    }

    @Override
    public OrderEntity getOrderEntityById(String entityNo, Long restaurantId) {
        OrderEntityId orderEntityId = new OrderEntityId();
        orderEntityId.setEntityNo(entityNo);
        orderEntityId.setRestaurantId(restaurantId);
        
        if (entityNo == null || restaurantId == null || !orderEntityRepository.existsById(orderEntityId)) {
            throw new ResourceNotFoundException("Record with entityNo " + entityNo
                    + " and restaurantId " + restaurantId + " not found");
        }
        return orderEntityMapper.orderEntityDAOToOrderEntity(orderEntityRepository.findById(orderEntityId).get());
    }

    @Override
    public List<OrderEntity> getOrderEntitiesByRestaurantId(Long restaurantId) {
        if (restaurantId == null || !orderEntityRepository.existsByOrderEntityIdRestaurantId(restaurantId)) {
            throw new ResourceNotFoundException("Record with restaurantId " + restaurantId + " not found");
        }
        return orderEntityMapper.orderEntityDAOListToOrderEntityList(orderEntityRepository.findAllByOrderEntityIdRestaurantId(restaurantId));
    }

    @Override
    public void deleteOrderEntity(String entityNo, Long restaurantId) {
        OrderEntityId orderEntityId = new OrderEntityId();
        orderEntityId.setEntityNo(entityNo);
        orderEntityId.setRestaurantId(restaurantId);
        
        if (entityNo == null || restaurantId == null || !orderEntityRepository.existsById(orderEntityId)) {
            throw new ResourceNotFoundException("Record with entityNo " + entityNo
                    + " and restaurantId " + restaurantId + " not found");
        }
        orderEntityRepository.deleteById(orderEntityId);
    }

}
