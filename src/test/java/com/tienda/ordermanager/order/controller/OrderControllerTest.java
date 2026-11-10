package com.tienda.ordermanager.order.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import com.tienda.ordermanager.common.exception.ResourceNotFoundException;
import com.tienda.ordermanager.order.dto.CreateOrderDTO;
import com.tienda.ordermanager.order.dto.OrderItemRequestDTO;
import com.tienda.ordermanager.order.dto.OrderItemResponseDTO;
import com.tienda.ordermanager.order.dto.OrderResponseDTO;
import com.tienda.ordermanager.order.model.OrderStatus;
import com.tienda.ordermanager.order.service.OrderService;

@WebMvcTest (OrderController.class)
public class OrderControllerTest {

    @Autowired 
    private MockMvc mockMvc;

    @Autowired 
    private JsonMapper objectMapper;

    // En Spring Boot moderno se utiliza @MockitoBean en reemplazo de @MockBean
    @MockitoBean 
    private OrderService orderService;

    @Test 
    @DisplayName ("POST /api/v1/orders - Debe responder 201 Created y  el DTO JSON del pedido")
    void createOrder_ValidPayload_Returns201Created() throws Exception {
        // 1. Arrange: Preparamos DTOs de entrada y respuesta esperada
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO(100L, 2);
        CreateOrderDTO createOrderDTO = new CreateOrderDTO(1L, List.of(itemRequest));

        OrderItemResponseDTO itemResponse = new OrderItemResponseDTO(
            10L, 100L, "Teclado Mecánico", 2, new BigDecimal("80.00"), new BigDecimal("160.00")
        );

        OrderResponseDTO expectedResponse = new OrderResponseDTO(
            500L, 1L, "cliente@tienda.com", OrderStatus.PENDING, new BigDecimal("160.00"), LocalDateTime.now(), List.of(itemResponse)
        );

        when(orderService.createOrder(any(CreateOrderDTO.class))).thenReturn(expectedResponse);

        // 2. Act & 3. Assert: Enviamos la petición HTTP POST y auditamos la respuesta
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createOrderDTO)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.Id", is(500)))
            .andExpect(jsonPath("$.userId", is(1)))
            .andExpect(jsonPath("$.userEmail", is("cliente@tienda.com")))
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.totalAmount", is(160.00)))
            .andExpect(jsonPath("$.items[0].productName", is("Teclado Mecánico")));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Debe rechazar petición con 400 Bad Request y estructura ProblemDetail si hay fallos de validación")
    void createOrder_InvalidPayload_Returns400BadRequest() throws Exception {
        // Arrange: Enviamos un userId nulo y una cantidad negativa (-5)
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO(100L, -5);
        CreateOrderDTO invalidDTO = new CreateOrderDTO(null, List.of(itemRequest));

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Error de validación")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.invalidFields.userId", is("El ID del usuario es obligatorio")))
                .andExpect(jsonPath("$.invalidFields['items[0].quantity']", is("La cantidad debe ser mayor que cero")));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - Debe responder 404 Not Found con formato RFC 7807 (ProblemDetail) si la orden no existe")
    void getOrderById_NotFound_Returns404ProblemDetail() throws Exception {
        // Arrange: El servicio simula el lanzamiento de la excepción de recurso no encontrado
        when(orderService.getOrderById(999L))
                .thenThrow(new ResourceNotFoundException("Pedido no encontrado con ID: 999"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Recurso no encontrado")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.detail", is("Pedido no encontrado con ID: 999")));
    }

}
