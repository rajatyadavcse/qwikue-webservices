package com.restaurant.service.mapper;

import org.mapstruct.Mapper;

import com.restaurant.service.dao.MenuDAO;
import com.restaurant.service.model.Menu;

@Mapper(componentModel = "spring")
public interface MenuMapper {

    MenuDAO menuToMenuDAO(Menu menu);

    Menu menuDAOToMenu(MenuDAO menuDAO);

}
