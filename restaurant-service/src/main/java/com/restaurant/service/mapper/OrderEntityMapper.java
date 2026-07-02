package com.restaurant.service.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.restaurant.service.dao.OrderEntityDAO;
import com.restaurant.service.model.OrderEntity;

@Mapper(componentModel = "spring")
public interface OrderEntityMapper {

    @Mapping(source = "entityNo", target = "orderEntityId.entityNo")
    @Mapping(source = "restaurantId", target = "orderEntityId.restaurantId")
    OrderEntityDAO orderEntityToOrderEntityDAO(OrderEntity orderEntity);

    @Mapping(source = "orderEntityId.entityNo", target = "entityNo")
    @Mapping(source = "orderEntityId.restaurantId", target = "restaurantId")
    OrderEntity orderEntityDAOToOrderEntity(OrderEntityDAO orderEntityDAO);

    List<OrderEntity> orderEntityDAOListToOrderEntityList(List<OrderEntityDAO> orderEntityDAOList);

}
