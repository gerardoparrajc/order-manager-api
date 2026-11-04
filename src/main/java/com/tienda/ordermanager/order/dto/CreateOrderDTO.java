package com.tienda.ordermanager.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateOrderDTO(
    @NotNull (message = "El ID del usuario es obligatorio")
    Long userId,

    @NotEmpty (message = "El pedido debe contener al menos un producto")
    @Valid // Activa la validación en cascada para cada elemento de la lista
    List<OrderItemRequestDTO> items
) {}
