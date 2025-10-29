package br.com.ecs.arquetipos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class WebhookRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    // Campos adicionais úteis para análise/marketing
    private String firstName;
    private String lastName;
    private String phone;
    private String document; // CPF/CNPJ
    private String zipcode;
    private String city;
    private String state;
    private String country;

    // Informações do produto/compra
    private String productId;
    private String productName;
    private String transactionId;
    private BigDecimal price;
    private String currency;
    private Instant purchaseDate;
}
