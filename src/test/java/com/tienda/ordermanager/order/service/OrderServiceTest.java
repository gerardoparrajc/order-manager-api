package com.tienda.ordermanager.order.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tienda.ordermanager.common.exception.InsufficientStockException;
import com.tienda.ordermanager.common.exception.ResourceNotFoundException;
import com.tienda.ordermanager.order.dto.CreateOrderDTO;
import com.tienda.ordermanager.order.dto.OrderItemRequestDTO;
import com.tienda.ordermanager.order.dto.OrderResponseDTO;
import com.tienda.ordermanager.order.model.Order;
import com.tienda.ordermanager.order.model.OrderStatus;
import com.tienda.ordermanager.order.repository.OrderRepository;
import com.tienda.ordermanager.product.model.Product;
import com.tienda.ordermanager.product.repository.ProductRepository;
import com.tienda.ordermanager.user.model.Role;
import com.tienda.ordermanager.user.model.User;
import com.tienda.ordermanager.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith (MockitoExtension.class)
public class OrderServiceTest {

    @Mock 
    private OrderRepository orderRepository;

    @Mock 
    private ProductRepository productRepository;

    @Mock 
    private UserRepository userRepository;

    @InjectMocks 
    private OrderService orderService;

    private User sampleUser;
    private Product sampleProduct;

    @BeforeEach 
    void setUp() {
        sampleUser = User.builder()
            .id(1L)
            .email("cliente@tienda.com")
            .password("password1234")
            .role(Role.ROLE_CLIENT)
            .build();

        sampleProduct = Product.builder()
            .id(100L)
            .name("Teclado mecánico")
            .price(new BigDecimal("80.00"))
            .stock(10)
            .active(true)
            .build();
    }

    @Test 
    @DisplayName ("Debe crear una orden exitosamente y actualizar el stock del producto")
    void createOrder_Success() {
        // Arrange
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO(100L, 2);
        CreateOrderDTO orderRequest = new CreateOrderDTO(1L, List.of(itemRequest));

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(productRepository.findByIdAndActiveTrue(100L)).thenReturn(Optional.of(sampleProduct));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order orderToSave = invocation.getArgument(0);
            orderToSave.setId(500L); // Simulate the database assigning an ID
            return orderToSave;
        });

        // Act
        OrderResponseDTO response = orderService.createOrder(orderRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.Id()).isEqualTo(500L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("160.00"));
        assertThat(response.items()).hasSize(1);

        // Verificar descuento de stock en memoria
        assertThat(sampleProduct.getStock()).isEqualTo(8);

        // Verificaciones con Mockito
        verify(userRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).findByIdAndActiveTrue(100L);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException cuando el usuario no existe")
    void createOrder_UserNotFound_ThrowsException() {
        // Arrange
        CreateOrderDTO orderRequest = new CreateOrderDTO(999L, List.of(new OrderItemRequestDTO(100L, 1)));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado con ID: 999");

        verify(productRepository, never()).findByIdAndActiveTrue(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Debe lanzar InsufficientStockException cuando la cantidad solicitada supera el stock")
    void createOrder_InsufficientStock_ThrowsException() {
        // Arrange
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO(100L, 15); // Solicitamos 15 (Stock es 10)
        CreateOrderDTO orderRequest = new CreateOrderDTO(1L, List.of(itemRequest));

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(productRepository.findByIdAndActiveTrue(100L)).thenReturn(Optional.of(sampleProduct));

        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Stock insuficiente para el producto 'Teclado mecánico'");

        // El stock no debe verse afectado
        assertThat(sampleProduct.getStock()).isEqualTo(10);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Debe buscar una orden existente por ID exitosamente")
    void getOrderById_Success() {
        // Arrange
        Order savedOrder = Order.builder()
                .id(500L)
                .user(sampleUser)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("80.00"))
                .build();

        when(orderRepository.findWithDetailsById(500L)).thenReturn(Optional.of(savedOrder));

        // Act
        OrderResponseDTO response = orderService.getOrderById(500L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.Id()).isEqualTo(500L);
        assertThat(response.userEmail()).isEqualTo("cliente@tienda.com");

        verify(orderRepository, times(1)).findWithDetailsById(500L);
    }
}
