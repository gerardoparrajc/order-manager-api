package com.tienda.ordermanager.order.dto;

import java.math.BigDecimal;

public record OrderItemResponseDTO(
    Long id,
    Long productId,
    String productName,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {}
