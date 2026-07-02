package com.restaurant.service.service;

import java.util.List;

import com.restaurant.service.model.OrderEntity;

public interface IOrderEntityService {

    public OrderEntity createOrderEntity(OrderEntity orderEntity);

    public OrderEntity updateOrderEntity(OrderEntity orderEntity);

    public OrderEntity getOrderEntityById(String entityNo, Long restaurantId);

    public List<OrderEntity> getOrderEntitiesByRestaurantId(Long restaurantId);

    public void deleteOrderEntity(String entityNo, Long restaurantId);

}
