package com.tienda.ordermanager.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.tienda.ordermanager.order.model.OrderStatus;

public record OrderResponseDTO(
    Long Id,
    Long userId,
    String userEmail,
    OrderStatus status,
    BigDecimal totalAmount,
    LocalDateTime createdAt,
    List<OrderItemResponseDTO> items
) {}
