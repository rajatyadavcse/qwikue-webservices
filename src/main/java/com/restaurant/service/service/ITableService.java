package com.restaurant.service.service;

import java.util.List;

import com.restaurant.service.model.Table;

public interface ITableService {

    public Table createTable(Table table);

    public Table updateTable(Table table);

    public Table getTableById(Long tableNo, Long restaurantId);

    public List<Table> getTablesByRestaurantId(Long restaurantId);

    public void deleteTable(Long tableNo, Long restaurantId);
}
