package com.tienda.ordermanager.order.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.tienda.ordermanager.order.dto.OrderItemResponseDTO;
import com.tienda.ordermanager.order.dto.OrderResponseDTO;
import com.tienda.ordermanager.order.model.Order;
import com.tienda.ordermanager.order.model.OrderItem;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    // 1. Mapeo principal de Order a OrderResponseDTO
    @Mapping (target = "userId", source = "user.id")
    @Mapping (target = "userEmail", source = "user.email")
    OrderResponseDTO toResponseDTO(Order order);

    // 2. Mapeo de OrderItem a OrderItemResponseDTO (usado automáticamente para la lista de items)
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "subtotal", source = "item", qualifiedByName = "calculateSubtotal")
    OrderItemResponseDTO toOrderItemResponseDTO(OrderItem item);

    // 3. Método de apoyo con lógica personalizada para calcular el subtotal
    @Named ("calculateSubtotal")
    default BigDecimal calculateSubtotal(OrderItem item) {
        if (item == null || item.getUnitPrice() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}
