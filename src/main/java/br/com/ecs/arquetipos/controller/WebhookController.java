package br.com.ecs.arquetipos.controller;

import br.com.ecs.arquetipos.dto.HotmartWebhookPayload;
import br.com.ecs.arquetipos.dto.WebhookRequest;
import br.com.ecs.arquetipos.model.Subscriber;
import br.com.ecs.arquetipos.model.Provider;
import br.com.ecs.arquetipos.service.SubscriberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final SubscriberService subscriberService;

    @PostMapping("/hotmart")
    public ResponseEntity<Map<String, Object>> receiveHotmartWebhook(@RequestBody HotmartWebhookPayload payload) {
        WebhookRequest request = parsePayload(payload, Provider.HOTMART);
        Subscriber saved = subscriberService.createOrUpdate(request, Provider.HOTMART);

        Map<String, Object> body = Map.of(
                "status", "success",
                "id", saved.getId(),
                "email", saved.getEmail(),
                "name", saved.getName(),
                "provider", "HOTMART"
        );

        return ResponseEntity.created(URI.create("/subscribers/" + saved.getId())).body(body);
    }

    @PostMapping("/eduzz")
    public ResponseEntity<Map<String, Object>> receiveEduzzWebhook(@RequestBody Map<String, Object> payload) {
        WebhookRequest request = parsePayload(payload, Provider.EDUZZ);
        Subscriber saved = subscriberService.createOrUpdate(request, Provider.EDUZZ);

        Map<String, Object> body = Map.of(
                "status", "success",
                "id", saved.getId(),
                "email", saved.getEmail(),
                "name", saved.getName(),
                "provider", "EDUZZ"
        );

        return ResponseEntity.created(URI.create("/subscribers/" + saved.getId())).body(body);
    }

    // Backward-compatible generic endpoint: tenta inferir o provider a partir do payload
    @PostMapping
    public ResponseEntity<Map<String, Object>> receiveGenericWebhook(@RequestBody Map<String, Object> payload,
                                                                     @RequestHeader Map<String, String> headers) {
        try {
            Provider provider = inferProviderFromHeaders(headers);
            if (provider == Provider.UNKNOWN) {
                // tenta inferir pelo payload
                provider = inferProviderFromPayload(payload);
            }

            WebhookRequest request = parsePayload(payload, provider);

            Subscriber saved;
            // Backward-compatibility: alguns consumidores ainda esperam o método antigo que recebe name/email
            if (provider == Provider.UNKNOWN) {
                // para testes/compatibilidade, usa HOTMART como provider ao chamar método antigo
                saved = subscriberService.createOrUpdate(request.getName(), request.getEmail(), Provider.HOTMART);
            } else {
                saved = subscriberService.createOrUpdate(request, provider);
            }

            if (saved == null) {
                Map<String, Object> error = Map.of(
                        "error", "Erro interno do servidor",
                        "message", "Falha ao persistir subscriber",
                        "code", "INTERNAL_ERROR"
                );
                return ResponseEntity.status(500).body(error);
            }

            Map<String, Object> body = Map.of(
                    "status", "success",
                    "id", saved.getId(),
                    "email", saved.getEmail(),
                    "name", saved.getName(),
                    "provider", provider.name()
            );

            return ResponseEntity.created(URI.create("/subscribers/" + saved.getId())).body(body);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> error = Map.of(
                    "error", "Bad request",
                    "message", ex.getMessage()
            );
            return ResponseEntity.badRequest().body(error);
        }
    }

    private Provider inferProviderFromHeaders(Map<String, String> headers) {
        for (String key : headers.keySet()) {
            String lk = key.toLowerCase(Locale.ROOT);
            if (lk.contains("hotmart")) return Provider.HOTMART;
            if (lk.contains("eduzz")) return Provider.EDUZZ;
        }
        return Provider.UNKNOWN;
    }

    private Provider inferProviderFromPayload(Map<String, Object> payload) {
        if (payload.containsKey("hotmart") || payload.containsKey("product") && payload.toString().toLowerCase().contains("hotmart")) {
            return Provider.HOTMART;
        }
        if (payload.containsKey("eduzz") || payload.toString().toLowerCase().contains("eduzz")) {
            return Provider.EDUZZ;
        }
        return Provider.UNKNOWN;
    }

    // Overload específico para o DTO Hotmart para evitar casts inseguros
    private WebhookRequest parsePayload(HotmartWebhookPayload payload, Provider provider) {
        WebhookRequest req = new WebhookRequest();
        if (payload == null) return req;

        HotmartWebhookPayload.DataNode data = payload.getData();
        if (data != null) {
            HotmartWebhookPayload.Buyer buyer = data.getBuyer();
            if (buyer != null) {
                req.setEmail(buyer.getEmail());
                req.setName(buyer.getName() != null ? buyer.getName() : buyer.getFirst_name());
                req.setFirstName(buyer.getFirst_name());
                req.setLastName(buyer.getLast_name());
                String phone = buyer.getCheckout_phone();
                String code = buyer.getCheckout_phone_code();
                if (phone != null) {
                    if (code != null) req.setPhone("+" + code + " " + phone);
                    else req.setPhone(phone);
                }
                req.setDocument(buyer.getDocument());
                HotmartWebhookPayload.Address addr = buyer.getAddress();
                if (addr != null) {
                    req.setZipcode(addr.getZipcode());
                    req.setCity(addr.getCity());
                    req.setState(addr.getState());
                    req.setCountry(addr.getCountry_iso() != null ? addr.getCountry_iso() : addr.getCountry());
                }
            }

            HotmartWebhookPayload.Product product = data.getProduct();
            if (product != null) {
                if (product.getId() != null) req.setProductId(String.valueOf(product.getId()));
                req.setProductName(product.getName());
            }

            HotmartWebhookPayload.Purchase purchase = data.getPurchase();
            if (purchase != null) {
                req.setTransactionId(purchase.getTransaction());
                HotmartWebhookPayload.Price price = purchase.getPrice();
                if (price != null) {
                    req.setCurrency(price.getCurrency_value());
                    if (price.getValue() != null) req.setPrice(price.getValue());
                }
                if (purchase.getApproved_date() != null) {
                    try { req.setPurchaseDate(Instant.ofEpochMilli(purchase.getApproved_date())); } catch (Exception ignored) {}
                }
            }
        }

        // Fallbacks mínimos
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email not found in webhook payload");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            String local = req.getEmail().split("@")[0];
            req.setName(local);
        }

        return req;
    }

    // Método genérico original (mantido para outros providers)
    @SuppressWarnings("unchecked")
    private WebhookRequest parsePayload(Map<String, Object> payload, Provider provider) {
        String email = findEmailRecursively(payload);
        String name = findNameRecursively(payload);

        // Regras para endpoint genérico (mais restritivas que o parser do Hotmart DTO)
        if (provider != Provider.HOTMART) {
            // exige que o payload contenha explicitamente o campo 'name'
            if (!payload.containsKey("name") && (name == null || name.isBlank())) {
                throw new IllegalArgumentException("Name is required");
            }
            // exige formato de email válido
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email not found in webhook payload");
            }
            if (!isValidEmail(email)) {
                throw new IllegalArgumentException("Invalid email format");
            }
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email not found in webhook payload");
        }
        if (name == null || name.isBlank()) {
            String local = email.split("@")[0];
            name = local;
        }

        WebhookRequest req = new WebhookRequest();
        req.setEmail(email);
        req.setName(name);

        // Campos adicionais - tentativa de extração heurística
        if (provider == Provider.HOTMART) {
            Object dataObj = payload.get("data");
            if (dataObj instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) dataObj;
                Object buyerObj = data.get("buyer");
                if (buyerObj instanceof Map) {
                    Map<String, Object> buyer = (Map<String, Object>) buyerObj;
                    req.setFirstName((String) buyer.getOrDefault("first_name", buyer.get("firstName")));
                    req.setLastName((String) buyer.getOrDefault("last_name", buyer.get("lastName")));
                    String phone = (String) buyer.get("checkout_phone");
                    String code = (String) buyer.get("checkout_phone_code");
                    if (phone != null) {
                        if (code != null) req.setPhone("+" + code + " " + phone);
                        else req.setPhone(phone);
                    }
                    req.setDocument((String) buyer.get("document"));
                    Object addressObj = buyer.get("address");
                    if (addressObj instanceof Map) {
                        Map<String, Object> address = (Map<String, Object>) addressObj;
                        req.setZipcode((String) address.get("zipcode"));
                        req.setCity((String) address.get("city"));
                        req.setState((String) address.get("state"));
                        req.setCountry((String) address.getOrDefault("country_iso", address.get("country")));
                    }
                }

                Object productObj = data.get("product");
                if (productObj instanceof Map) {
                    Map<String, Object> product = (Map<String, Object>) productObj;
                    Object pid = product.get("id");
                    if (pid != null) req.setProductId(String.valueOf(pid));
                    req.setProductName((String) product.get("name"));
                }

                Object purchaseObj = data.get("purchase");
                if (purchaseObj instanceof Map) {
                    Map<String, Object> purchase = (Map<String, Object>) purchaseObj;
                    req.setTransactionId((String) purchase.get("transaction"));
                    Object priceObj = purchase.get("price");
                    if (priceObj instanceof Map) {
                        Map<String, Object> priceMap = (Map<String, Object>) priceObj;
                        Object val = priceMap.get("value");
                        if (val instanceof Number) {
                            req.setPrice(BigDecimal.valueOf(((Number) val).doubleValue()));
                        } else if (val instanceof String) {
                            try { req.setPrice(new BigDecimal((String) val)); } catch (Exception ignored) {}
                        }
                        req.setCurrency((String) priceMap.get("currency_value"));
                    }
                    Object approved = purchase.get("approved_date");
                    if (approved instanceof Number) {
                        long ts = ((Number) approved).longValue();
                        try { req.setPurchaseDate(Instant.ofEpochMilli(ts)); } catch (Exception ignored) {}
                    }
                }
            }
        } else {
            req.setFirstName(findStringByKeyRecursively(payload, Arrays.asList("first_name","firstName","first")));
            req.setLastName(findStringByKeyRecursively(payload, Arrays.asList("last_name","lastName","last")));
            req.setPhone(findStringByKeyRecursively(payload, Arrays.asList("phone","checkout_phone","phone_number")));
            req.setDocument(findStringByKeyRecursively(payload, Arrays.asList("document","cpf","cnpj")));
            req.setZipcode(findStringByKeyRecursively(payload, Arrays.asList("zipcode","zip","postal_code")));
            req.setCity(findStringByKeyRecursively(payload, Arrays.asList("city")));
            req.setState(findStringByKeyRecursively(payload, Arrays.asList("state")));
            req.setCountry(findStringByKeyRecursively(payload, Arrays.asList("country","country_iso")));
        }

        return req;
    }

    // Pequena validação de email (não substitui validação robusta)
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    @SuppressWarnings("unchecked")
    private String findEmailRecursively(Object node) {
        if (node == null) return null;
        if (node instanceof String) {
            String s = (String) node;
            if (s.contains("@")) return s;
            return null;
        }
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String key = e.getKey().toLowerCase(Locale.ROOT);
                if (key.contains("email") || key.contains("e-mail") || key.contains("buyer") || key.contains("customer")) {
                    Object val = e.getValue();
                    String found = findEmailRecursively(val);
                    if (found != null) return found;
                }
            }
            for (Object v : map.values()) {
                String found = findEmailRecursively(v);
                if (found != null) return found;
            }
        }
        if (node instanceof Collection) {
            for (Object item : (Collection<?>) node) {
                String found = findEmailRecursively(item);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String findNameRecursively(Object node) {
        if (node == null) return null;
        if (node instanceof String) {
            String s = (String) node;
            // Não considerar strings que parecem emails como nome
            if (s.contains("@")) return null;
            return s;
        }
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String key = e.getKey().toLowerCase(Locale.ROOT);
                if (key.contains("name") || key.contains("nome") || key.contains("buyer") || key.contains("customer")) {
                    Object val = e.getValue();
                    if (val instanceof String) return (String) val;
                    String found = findNameRecursively(val);
                    if (found != null) return found;
                }
            }
            for (Object v : map.values()) {
                String found = findNameRecursively(v);
                if (found != null) return found;
            }
        }
        if (node instanceof Collection) {
            for (Object item : (Collection<?>) node) {
                String found = findNameRecursively(item);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String findStringByKeyRecursively(Object node, List<String> keysLower) {
        if (node == null) return null;
        if (node instanceof String) return (String) node;
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String key = e.getKey().toLowerCase(Locale.ROOT);
                for (String k : keysLower) {
                    if (key.equals(k)) {
                        Object val = e.getValue();
                        if (val instanceof String) return (String) val;
                        String found = findStringByKeyRecursively(val, keysLower);
                        if (found != null) return found;
                    }
                }
            }
            for (Object v : map.values()) {
                String found = findStringByKeyRecursively(v, keysLower);
                if (found != null) return found;
            }
        }
        if (node instanceof Collection) {
            for (Object item : (Collection<?>) node) {
                String found = findStringByKeyRecursively(item, keysLower);
                if (found != null) return found;
            }
        }
        return null;
    }
}
