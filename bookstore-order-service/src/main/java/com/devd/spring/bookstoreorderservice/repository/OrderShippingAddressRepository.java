package com.devd.spring.bookstoreorderservice.repository;

import com.devd.spring.bookstoreorderservice.repository.dao.OrderShippingAddress;
import org.springframework.data.repository.CrudRepository;

/**
 * @author Deepak Srivastav, Date : 07-Dec-2020
 */
public interface OrderShippingAddressRepository extends CrudRepository<OrderShippingAddress, String> {
    OrderShippingAddress findByOrderId(String orderId);
}
