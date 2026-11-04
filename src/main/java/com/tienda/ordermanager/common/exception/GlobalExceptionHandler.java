package com.tienda.ordermanager.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice 
public class GlobalExceptionHandler {

    // Maneja búsquedas de recursos inexistentes (HTTP 404)
    @ExceptionHandler (ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso no encontrado");
        problem.setType(URI.create("https://api.tienda.com/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // Maneja falta de stock en la creación de pedidos (HTTP 400)
    @ExceptionHandler (InsufficientStockException.class)
    public ProblemDetail handleInssuficientStockException(InsufficientStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Stock insuficiente");
        problem.setType(URI.create("https://api.tienda.com/errors/insufficient-stock"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // Maneja errores de validación de Bean Validation / DTOs (HTTP 400)
    @ExceptionHandler (MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La petición contiene datos de entrada no válidos");
        problem.setTitle("Error de validación");
        problem.setType(URI.create("https://api.tienda.com/errors/validation-error"));
        problem.setProperty("invalidFields", errors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
