package com.tienda.ordermanager.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequestDTO(

    @NotNull (message = "El ID del producto es obligatorio")
    Long productId,

    @NotNull (message = "La cantidad es obligatoria")
    @Positive (message = "La cantidad debe ser mayor que cero")
    Integer quantity
) {}