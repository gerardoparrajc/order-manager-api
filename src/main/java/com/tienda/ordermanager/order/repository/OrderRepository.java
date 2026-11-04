package com.tienda.ordermanager.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tienda.ordermanager.order.model.Order;

@Repository 
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Evita consultas N+1 cargando en un solo JOIN el usuario, los items y el producto
    @EntityGraph (attributePaths = {"user", "items", "items.product"})
    Optional<Order> findWithDetailsById(Long id);
}
