package com.tienda.ordermanager.order.service;

import com.tienda.ordermanager.common.exception.InsufficientStockException;
import com.tienda.ordermanager.common.exception.ResourceNotFoundException;
import com.tienda.ordermanager.order.dto.CreateOrderDTO;
import com.tienda.ordermanager.order.dto.OrderItemRequestDTO;
import com.tienda.ordermanager.order.dto.OrderItemResponseDTO;
import com.tienda.ordermanager.order.dto.OrderResponseDTO;
import com.tienda.ordermanager.order.mapper.OrderMapper;
import com.tienda.ordermanager.order.model.Order;
import com.tienda.ordermanager.order.model.OrderStatus;
import com.tienda.ordermanager.order.repository.OrderRepository;
import com.tienda.ordermanager.product.model.Product;
import com.tienda.ordermanager.product.repository.ProductRepository;
import com.tienda.ordermanager.user.model.Role;
import com.tienda.ordermanager.user.model.User;
import com.tienda.ordermanager.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    private User sampleUser;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("cliente@tienda.com")
                .password("password123")
                .role(Role.ROLE_CLIENT)
                .build();

        sampleProduct = Product.builder()
                .id(100L)
                .name("Teclado Mecánico")
                .price(new BigDecimal("80.00"))
                .stock(10)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Debe crear una orden exitosamente y actualizar el stock del producto")
    void createOrder_Success() {
        // 1. Arrange (Preparación)
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO(100L, 2);
        CreateOrderDTO orderRequest = new CreateOrderDTO(1L, List.of(itemRequest));

        Order savedOrderInDb = Order.builder()
                .id(500L)
                .user(sampleUser)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("160.00"))
                .createdAt(LocalDateTime.now())
                .build();

        OrderItemResponseDTO itemResponseDTO = new OrderItemResponseDTO(
                10L, 100L, "Teclado Mecánico", 2, new BigDecimal("80.00"), new BigDecimal("160.00")
        );

        OrderResponseDTO expectedResponseDTO = new OrderResponseDTO(
                500L, 1L, "cliente@tienda.com", OrderStatus.PENDING, 
                new BigDecimal("160.00"), LocalDateTime.now(), List.of(itemResponseDTO)
        );

        // Definimos el comportamiento esperado de los Mocks
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(productRepository.findByIdAndActiveTrue(100L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrderInDb);
        when(orderMapper.toResponseDTO(savedOrderInDb)).thenReturn(expectedResponseDTO);

        // 2. Act (Ejecución)
        OrderResponseDTO actualResponse = orderService.createOrder(orderRequest);

        // 3. Assert (Verificaciones)
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.Id()).isEqualTo(500L);
        assertThat(actualResponse.userId()).isEqualTo(1L);
        assertThat(actualResponse.totalAmount()).isEqualByComparingTo(new BigDecimal("160.00"));

        // Verificamos que el stock en el objeto en memoria disminuyó de 10 a 8
        assertThat(sampleProduct.getStock()).isEqualTo(8);

        // Verificamos que las dependencias fueron invocadas el número correcto de veces
        verify(userRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).findByIdAndActiveTrue(100L);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderMapper, times(1)).toResponseDTO(savedOrderInDb);
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

        // Verificamos que al fallar la primera validación, NUNCA se tocaron los repositorios de productos ni órdenes
        verify(productRepository, never()).findByIdAndActiveTrue(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Debe lanzar InsufficientStockException cuando la cantidad solicitada supera el stock disponible")
    void createOrder_InsufficientStock_ThrowsException() {
        // Arrange: Solicitamos 15 unidades cuando el stock es solo 10
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO(100L, 15);
        CreateOrderDTO orderRequest = new CreateOrderDTO(1L, List.of(itemRequest));

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(productRepository.findByIdAndActiveTrue(100L)).thenReturn(Optional.of(sampleProduct));

        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Stock insuficiente para el producto 'Teclado Mecánico'");

        // Garantizamos que el stock no fue alterado tras el error
        assertThat(sampleProduct.getStock()).isEqualTo(10);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Debe obtener una orden por ID exitosamente invocando al mapeador")
    void getOrderById_Success() {
        // Arrange
        Order savedOrder = Order.builder()
                .id(500L)
                .user(sampleUser)
                .status(OrderStatus.PENDING)
                .build();

        OrderResponseDTO expectedDTO = new OrderResponseDTO(
                500L, 1L, "cliente@tienda.com", OrderStatus.PENDING,
                new BigDecimal("80.00"), LocalDateTime.now(), List.of()
        );

        when(orderRepository.findWithDetailsById(500L)).thenReturn(Optional.of(savedOrder));
        when(orderMapper.toResponseDTO(savedOrder)).thenReturn(expectedDTO);

        // Act
        OrderResponseDTO response = orderService.getOrderById(500L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.Id()).isEqualTo(500L);
        verify(orderRepository, times(1)).findWithDetailsById(500L);
        verify(orderMapper, times(1)).toResponseDTO(savedOrder);
    }
}