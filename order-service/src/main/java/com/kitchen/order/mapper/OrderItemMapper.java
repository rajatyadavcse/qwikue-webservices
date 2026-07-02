package com.kitchen.order.mapper;

import com.kitchen.order.dao.OrderItemDAO;
import com.kitchen.order.dto.response.OrderItemResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    OrderItemResponse orderItemDAOToOrderItemResponse(OrderItemDAO orderItemDAO);

    List<OrderItemResponse> orderItemDAOListToResponseList(List<OrderItemDAO> orderItemDAOs);
}
