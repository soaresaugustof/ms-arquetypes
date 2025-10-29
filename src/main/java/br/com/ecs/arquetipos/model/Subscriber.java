package br.com.ecs.arquetipos.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "subscribers", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Provider provider = Provider.UNKNOWN;

    // Campos adicionais para análises de marketing
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
