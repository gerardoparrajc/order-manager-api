package com.tienda.ordermanager.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import jakarta.transaction.Transactional;


import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.tienda.ordermanager.common.exception.InsufficientStockException;
import com.tienda.ordermanager.order.dto.CreateOrderDTO;
import com.tienda.ordermanager.order.dto.OrderItemRequestDTO;
import com.tienda.ordermanager.order.dto.OrderResponseDTO;
import com.tienda.ordermanager.order.model.Order;
import com.tienda.ordermanager.order.model.OrderStatus;
import com.tienda.ordermanager.order.repository.OrderRepository;
import com.tienda.ordermanager.order.service.OrderService;
import com.tienda.ordermanager.product.model.Product;
import com.tienda.ordermanager.product.repository.ProductRepository;
import com.tienda.ordermanager.user.model.Role;
import com.tienda.ordermanager.user.model.User;
import com.tienda.ordermanager.user.repository.UserRepository;

@SpringBootTest (webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional 
public class OrderIntegrationTest {

    // 1. Levanta un contenedor Docker real con PostgreSQL 16
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgresContainer = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired 
    private OrderService orderService;

    @Autowired 
    private UserRepository userRepository;

    @Autowired 
    private ProductRepository productRepository;

    @Autowired 
    private OrderRepository orderRepository;

    private User persistedUser;
    private Product persistedProduct;

    @BeforeEach 
    void setupData() {
        // Preparamos datos iniciales reales insertados directamente en la base de datos
        User user = User.builder()
            .email("cliente.real@tienda.com")
            .password("securePass123")
            .role(Role.ROLE_CLIENT)
            .build();
        persistedUser = userRepository.save(user);

        Product product = Product.builder()
            .name("Monitor Gaming 4k")
            .price(new BigDecimal("350.00"))
            .stock(5)
            .active(true)
            .build();
        persistedProduct = productRepository.save(product);
    }

    @Test 
    @DisplayName ("Debe persistir una orden en PostgreSQL real, generar IDs y descontar el stock")
    void createOrder_RealDatabase_Success() {
        // Arrange: Creamos la petición DTO apuntando a los IDs reales guardados en la base de datos
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO(persistedProduct.getId(), 2);
        CreateOrderDTO createOrderDTO = new CreateOrderDTO(persistedUser.getId(), List.of(itemRequest));

        // Act: Ejecutamos el flujo del servicio completo
        OrderResponseDTO response = orderService.createOrder(createOrderDTO);

        // Assert 1: Validar el DTO de respuesta
        assertThat(response).isNotNull();
        assertThat(response.Id()).isNotNull(); // El ID fue asignado por la secuencia BIGSERIAL de PostgreSQL
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

        // Asssert 2: Verificar la persistencia REAL en las tablas de PostgreSQL
        Order savedOrderInDb = orderRepository.findWithDetailsById(response.Id()).orElseThrow();
        assertThat(savedOrderInDb.getUser().getEmail()).isEqualTo("cliente.real@tienda.com");
        assertThat(savedOrderInDb.getItems()).hasSize(1);
        assertThat(savedOrderInDb.getItems().get(0).getUnitPrice()).isEqualByComparingTo(new BigDecimal("350.00"));

        // Assert 3: Verificar que el stock se descontó REALMENTE en la tabla 'products'
        Product updatedProduct = productRepository.findById(persistedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(3); // 5 iniciales - 2 comprados
    }

    @Test
    @DisplayName ("Debe hacer un ROLLBACK automático en PostgreSQL si ocurre un error de stock")
    void createOrder_InsufficientStock_RollbackInDatabase() {
        // Arrange: Intentamos comprar 10 unidades (solo hay 5 disponibles en la BD)
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO(persistedProduct.getId(), 10);
        CreateOrderDTO createOrderDTO = new CreateOrderDTO(persistedUser.getId(), List.of(itemRequest));

        // Act & Assert Exception
        assertThatThrownBy(() -> orderService.createOrder(createOrderDTO))
            .isInstanceOf(InsufficientStockException.class);

        Product productInDb = productRepository.findById(persistedProduct.getId()).orElseThrow();
        assertThat(productInDb.getStock()).isEqualTo(5); // El stock original permanece intacto
    }
}
