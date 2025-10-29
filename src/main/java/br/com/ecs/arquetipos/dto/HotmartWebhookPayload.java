package br.com.ecs.arquetipos.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class HotmartWebhookPayload {
    private String id;
    private Long creation_date;
    private String event;
    private String version;
    private DataNode data;

    @Data
    @NoArgsConstructor
    public static class DataNode {
        private Product product;
        private List<Object> affiliates;
        private Buyer buyer;
        private Producer producer;
        private List<Object> commissions;
        private Purchase purchase;
        private Subscription subscription;
    }

    @Data
    @NoArgsConstructor
    public static class Product {
        private Long id;
        private String ucode;
        private String name;
    }

    @Data
    @NoArgsConstructor
    public static class Buyer {
        private String email;
        private String name;
        private String first_name;
        private String last_name;
        private String checkout_phone;
        private String checkout_phone_code;
        private String document;
        private Address address;
    }

    @Data
    @NoArgsConstructor
    public static class Address {
        private String zipcode;
        private String country;
        private String number;
        private String address;
        private String city;
        private String state;
        private String neighborhood;
        private String complement;
        private String country_iso;
    }

    @Data
    @NoArgsConstructor
    public static class Purchase {
        private Long approved_date;
        private Price price;
        private String transaction;
    }

    @Data
    @NoArgsConstructor
    public static class Price {
        private BigDecimal value;
        private String currency_value;
    }

    @Data
    @NoArgsConstructor
    public static class Producer {
        private String name;
    }

    @Data
    @NoArgsConstructor
    public static class Subscription {
        private String status;
    }
}

