package com.kitchen.order.mapper;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.dto.response.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {

    /**
     * Maps OrderDAO → OrderResponse.
     * items list is mapped via OrderItemMapper (registered in 'uses').
     */
    @Mapping(source = "items", target = "items")
    @Mapping(target = "razorpayKeyId", ignore = true)
    OrderResponse orderDAOToOrderResponse(OrderDAO orderDAO);

    List<OrderResponse> orderDAOListToResponseList(List<OrderDAO> orderDAOs);
}
