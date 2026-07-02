package com.kitchen.order.mapper;

import com.kitchen.order.dao.CustomerDAO;
import com.kitchen.order.dto.response.CustomerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    CustomerResponse customerDAOToCustomerResponse(CustomerDAO customerDAO);
}
