package com.kitchen.order.repository;

import com.kitchen.order.dao.CustomerDAO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerDAO, Long> {
    Optional<CustomerDAO> findByPhone(String phone);
}
