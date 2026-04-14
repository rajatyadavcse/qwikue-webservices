package com.restaurant.service.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.restaurant.service.dao.TableDAO;
import com.restaurant.service.dao.TableId;
import com.restaurant.service.exception.ResourceAlreadyExistsException;
import com.restaurant.service.exception.ResourceNotFoundException;
import com.restaurant.service.mapper.TableMapper;
import com.restaurant.service.model.Table;
import com.restaurant.service.repository.TableRepository;

@Service
public class TableServiceImpl implements ITableService {

    @Autowired
    TableRepository tableRepository;

    @Autowired
    TableMapper tableMapper;

    @Override
    public Table createTable(Table table) {
        if (table.getTableNo() == null || table.getRestaurantId() == null) {
            throw new ResourceNotFoundException("Table No and Restaurantid is required for creating a table");
        }
        TableId tableId = new TableId();
        tableId.setTableNo(table.getTableNo());
        tableId.setRestaurantId(table.getRestaurantId());
        if (tableRepository.existsById(tableId)) {
            throw new ResourceAlreadyExistsException("A record with tableNo " + table.getTableNo()
                    + " and restaurantId " + table.getRestaurantId()
                    + " already exists, Please try with different tableNo or try updating the existing record");
        }
        TableDAO tableDAO = tableMapper.tableToTableDAO(table);
        return tableMapper.tableDAOToTable(tableRepository.save(tableDAO));
    }

    @Override
    public Table updateTable(Table table) {
        TableId tableId = new TableId();
        tableId.setTableNo(table.getTableNo());
        tableId.setRestaurantId(table.getRestaurantId());
        if (table.getTableNo() == null || table.getRestaurantId() == null || !tableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Table ID is required for update");
        }

        TableDAO tableDAO = tableMapper.tableToTableDAO(table);
        return tableMapper.tableDAOToTable(tableRepository.save(tableDAO));
    }

    @Override
    public Table getTableById(Long tableNo, Long restaurantId) {
        TableId tableId = new TableId();
        tableId.setTableNo(tableNo);
        tableId.setRestaurantId(restaurantId);
        if (tableNo == null || restaurantId == null || !tableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Record with tableNo " + tableNo
                    + " and restaurantId " + restaurantId + " not found");
        }
        return tableMapper.tableDAOToTable(tableRepository.findById(tableId).get());
    }

    @Override
    public List<Table> getTablesByRestaurantId(Long restaurantId) {
        if (restaurantId == null || !tableRepository.existsByRestaurantId(restaurantId)) {
            throw new ResourceNotFoundException("Record with restaurantId " + restaurantId + " not found");
        }
        return tableMapper.tableDAOListToTableList(tableRepository.findAllByRestaurantId(restaurantId));
    }

    @Override
    public void deleteTable(Long tableNo, Long restaurantId) {
        TableId tableId = new TableId();
        tableId.setTableNo(tableNo);
        tableId.setRestaurantId(restaurantId);
        if (tableNo == null || restaurantId == null || !tableRepository.existsById(tableId)) {
            throw new ResourceNotFoundException("Record with tableNo " + tableNo
                    + " and restaurantId " + restaurantId + " not found");
        }
        tableRepository.deleteById(tableId);
    }

}
