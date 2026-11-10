package com.tienda.ordermanager.order.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.naming.InsufficientResourcesException;

import org.springframework.stereotype.Service;

import com.tienda.ordermanager.common.exception.InsufficientStockException;
import com.tienda.ordermanager.common.exception.ResourceNotFoundException;
import com.tienda.ordermanager.order.dto.CreateOrderDTO;
import com.tienda.ordermanager.order.dto.OrderItemRequestDTO;
import com.tienda.ordermanager.order.dto.OrderItemResponseDTO;
import com.tienda.ordermanager.order.dto.OrderResponseDTO;
import com.tienda.ordermanager.order.mapper.OrderMapper;
import com.tienda.ordermanager.order.model.Order;
import com.tienda.ordermanager.order.model.OrderItem;
import com.tienda.ordermanager.order.model.OrderStatus;
import com.tienda.ordermanager.order.repository.OrderRepository;
import com.tienda.ordermanager.product.model.Product;
import com.tienda.ordermanager.product.repository.ProductRepository;
import com.tienda.ordermanager.user.model.User;
import com.tienda.ordermanager.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service 
@RequiredArgsConstructor 
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper; // Inyectado por Spring gracias a ComponentModel = "spring"

    @Transactional 
    public OrderResponseDTO createOrder(CreateOrderDTO request) {
        // 1. Validar que el usuario exista (acceso vía accesor de record Java 21)
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " +  request.userId()));

        // 2. Instanciar la order base
        Order order = Order.builder()
            .user(user)
            .status(OrderStatus.PENDING)
            .totalAmount(BigDecimal.ZERO)
            .items(new ArrayList<>())
            .build();

        BigDecimal calculatedTotal = BigDecimal.ZERO;

        // 3. Procesar cada item solicitando la actualización de stock
        for (OrderItemRequestDTO itemDto : request.items()) {
            Product product = productRepository.findByIdAndActiveTrue(itemDto.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Producto no encontrado o inactivo con ID: " + itemDto.productId()
                ));

            // Validación de stock disponible
            if (product.getStock() < itemDto.quantity()) {
                throw new InsufficientStockException(
                    String.format("Stock insuficiente para el producto '%s'. Disponible: %d, Solicitado: %d",
                        product.getName(), product.getStock(), itemDto.quantity()
                    )
                );
            }

            // Descontar el stock en memoria (JPA lo sincroniza con PostgreSQL al hacer commit) 
            product.setStock(product.getStock() - itemDto.quantity());

            // Calculo exacto de subtotal usando BigDecimal
            BigDecimal itemSubtotal = product.getPrice().multiply(BigDecimal.valueOf(itemDto.quantity()));
            calculatedTotal = calculatedTotal.add(itemSubtotal);

            // Crear el OrderItem congelando el precio unitario del momento de la compra
            OrderItem orderItem = OrderItem.builder()
                .product(product)
                .quantity(itemDto.quantity())
                .unitPrice(product.getPrice())
                .build();

            // Usar el método helper bidireccional
            order.addOrderItem(orderItem);
        }

        order.setTotalAmount(calculatedTotal);

        // 4. Guardar la orden (cascade = ALL guarda automáticamente los OrderItems)
        Order savedOrder = orderRepository.save(order);

        return orderMapper.toResponseDTO(savedOrder);
    }

    @Transactional (readOnly = true)
    public OrderResponseDTO getOrderById(Long id) {
        Order order = orderRepository.findWithDetailsById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + id));

        return orderMapper.toResponseDTO(order);
    }


    // SI se utiliza MapStruct, este método ya no es necesario
    private OrderResponseDTO mapToResponseDTO(Order order) {
        List<OrderItemResponseDTO> itemDTOs = order.getItems().stream()
            .map(item -> new OrderItemResponseDTO(
                item.getId(), 
                item.getProduct().getId(), 
                item.getProduct().getName(), 
                item.getQuantity(), 
                item.getUnitPrice(), 
            item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
        )).toList();

        return new OrderResponseDTO(
            order.getId(),
            order.getUser().getId(),
            order.getUser().getEmail(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            itemDTOs
        );
    }
}
