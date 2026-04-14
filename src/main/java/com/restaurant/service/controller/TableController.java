package com.restaurant.service.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.service.model.Table;
import com.restaurant.service.service.ITableService;

@RestController
@RequestMapping("/tables")
public class TableController {

    @Autowired
    ITableService tableService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Table> createTable(@RequestBody Table table) {
        return new ResponseEntity<>(tableService.createTable(table), HttpStatus.CREATED);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Table>> getTablesByRestaurantId(@RequestParam Long restaurantId) {
        return new ResponseEntity<>(tableService.getTablesByRestaurantId(restaurantId), HttpStatus.OK);
    }

    @GetMapping(value = "/{tableNo}/restaurant/{restaurantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Table> getTableById(@PathVariable Long tableNo, @PathVariable Long restaurantId) {
        return new ResponseEntity<>(tableService.getTableById(tableNo, restaurantId), HttpStatus.OK);
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Table> updateTable(@RequestBody Table table) {
        return new ResponseEntity<>(tableService.updateTable(table), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{tableNo}/restaurant/{restaurantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteTable(@PathVariable Long tableNo, @PathVariable Long restaurantId) {
        tableService.deleteTable(tableNo, restaurantId);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
