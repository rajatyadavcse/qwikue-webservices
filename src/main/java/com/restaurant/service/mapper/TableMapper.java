package com.restaurant.service.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.restaurant.service.dao.TableDAO;
import com.restaurant.service.model.Table;

@Mapper(componentModel = "spring")
public interface TableMapper {

    @Mapping(source = "tableNo", target = "tableId.tableNo")
    @Mapping(source = "restaurantId", target = "tableId.restaurantId")
    TableDAO tableToTableDAO(Table table);

    @Mapping(source = "tableId.tableNo", target = "tableNo")
    @Mapping(source = "tableId.restaurantId", target = "restaurantId")
    Table tableDAOToTable(TableDAO tableDAO);

    List<Table> tableDAOListToTableList(List<TableDAO> tableDAOList);
}
