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
        WebhookRequest request = parseEduzzPayload(payload);
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

    @PostMapping("/kiwify")
    public ResponseEntity<Map<String, Object>> receiveKiwifyWebhook(@RequestBody Map<String, Object> payload) {
        WebhookRequest request = parseKiwifyPayload(payload);
        Subscriber saved = subscriberService.createOrUpdate(request, Provider.KIWIFY);

        Map<String, Object> body = Map.of(
                "status", "success",
                "id", saved.getId(),
                "email", saved.getEmail(),
                "name", saved.getName(),
                "provider", "KIWIFY"
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

    // Parser específico para payloads do Eduzz (usando o exemplo fornecido)
    @SuppressWarnings("unchecked")
    private WebhookRequest parseEduzzPayload(Map<String, Object> payload) {
        WebhookRequest req = new WebhookRequest();
        if (payload == null) return req;

        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map)) {
            throw new IllegalArgumentException("Invalid eduzz payload: missing data node");
        }
        Map<String, Object> data = (Map<String, Object>) dataObj;

        // Prefer buyer, se não existir usa student
        Map<String, Object> person = null;
        if (data.get("buyer") instanceof Map) person = (Map<String, Object>) data.get("buyer");
        else if (data.get("student") instanceof Map) person = (Map<String, Object>) data.get("student");

        if (person != null) {
            req.setEmail((String) person.get("email"));
            req.setName((String) person.get("name"));

            // tenta separar first/last name simples
            String fullName = (String) person.get("name");
            if (fullName != null && !fullName.isBlank()) {
                String[] parts = fullName.trim().split(" ");
                req.setFirstName(parts[0]);
                if (parts.length > 1) req.setLastName(parts[parts.length - 1]);
            }

            // telefones: cellphone, phone, phone2
            String phone = (String) person.get("cellphone");
            if (phone == null) phone = (String) person.get("phone");
            if (phone == null) phone = (String) person.get("phone2");
            req.setPhone(phone);

            req.setDocument((String) person.get("document"));

            Object addressObj = person.get("address");
            if (addressObj instanceof Map) {
                Map<String, Object> addr = (Map<String, Object>) addressObj;
                req.setZipcode((String) addr.get("zipCode"));
                req.setCity((String) addr.get("city"));
                req.setState((String) addr.get("state"));
                req.setCountry((String) addr.getOrDefault("country", addr.get("country_iso")));
            }
        }

        // Transaction/ids
        if (data.get("transaction") instanceof Map) {
            Map<String, Object> tx = (Map<String, Object>) data.get("transaction");
            Object tid = tx.get("id");
            if (tid != null) req.setTransactionId(String.valueOf(tid));
        } else if (data.get("id") != null) {
            req.setTransactionId(String.valueOf(data.get("id")));
        }

        // Items / product
        Object itemsObj = data.get("items");
        if (itemsObj instanceof Collection) {
            for (Object it : (Collection<Object>) itemsObj) {
                if (it instanceof Map) {
                    Map<String, Object> item = (Map<String, Object>) it;
                    Object pid = item.get("productId");
                    if (pid != null && (req.getProductId() == null || req.getProductId().isBlank())) {
                        req.setProductId(String.valueOf(pid));
                    }
                    if (req.getProductName() == null) req.setProductName((String) item.get("name"));
                    // pega apenas o primeiro item útil
                    break;
                }
            }
        }

        // Preço / valor pago
        Object paidObj = data.get("paid");
        if (paidObj instanceof Map) {
            Map<String, Object> paid = (Map<String, Object>) paidObj;
            Object val = paid.get("value");
            if (val instanceof Number) req.setPrice(BigDecimal.valueOf(((Number) val).doubleValue()));
            else if (val instanceof String) {
                try { req.setPrice(new BigDecimal((String) val)); } catch (Exception ignored) {}
            }
            req.setCurrency((String) paid.get("currency"));
        } else {
            Object priceObj = data.get("price");
            if (priceObj instanceof Map) {
                Map<String, Object> price = (Map<String, Object>) priceObj;
                Object val = price.get("value");
                if (val instanceof Number) req.setPrice(BigDecimal.valueOf(((Number) val).doubleValue()));
                else if (val instanceof String) {
                    try { req.setPrice(new BigDecimal((String) val)); } catch (Exception ignored) {}
                }
                req.setCurrency((String) price.get("currency"));
            }
        }

        // Datas de pagamento
        Object paidAt = data.get("paidAt");
        if (paidAt instanceof String) {
            try { req.setPurchaseDate(Instant.parse((String) paidAt)); } catch (Exception ignored) {}
        }

        // Validações mínimas e fallbacks
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email not found in webhook payload");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            String local = req.getEmail().split("@")[0];
            req.setName(local);
        }

        return req;
    }

    @SuppressWarnings("unchecked")
    private WebhookRequest parseKiwifyPayload(Map<String, Object> payload) {
        WebhookRequest req = new WebhookRequest();
        if (payload == null) return req;

        // Customer block
        Object custObj = payload.get("Customer");
        if (custObj instanceof Map) {
            Map<String, Object> customer = (Map<String, Object>) custObj;
            req.setEmail((String) customer.get("email"));
            String name = (String) customer.getOrDefault("full_name", customer.get("first_name"));
            req.setName(name);
            req.setFirstName((String) customer.get("first_name"));
            req.setPhone((String) customer.get("mobile"));
            // CPF field name may be 'CPF' or 'cpf'
            req.setDocument((String) customer.getOrDefault("CPF", customer.get("cpf")));

            // address
            req.setZipcode((String) customer.get("zipcode"));
            req.setCity((String) customer.get("city"));
            req.setState((String) customer.get("state"));
            req.setCountry(null);
        }

        // Product
        Object prodObj = payload.get("Product");
        if (prodObj instanceof Map) {
            Map<String, Object> prod = (Map<String, Object>) prodObj;
            Object pid = prod.get("product_id");
            if (pid != null) req.setProductId(String.valueOf(pid));
            req.setProductName((String) prod.get("product_name"));
        }

        // Transaction id
        if (payload.get("order_id") != null) req.setTransactionId(String.valueOf(payload.get("order_id")));
        else if (payload.get("order_ref") != null) req.setTransactionId(String.valueOf(payload.get("order_ref")));

        // Price: prefer Commissions.my_commission or Commissions.product_base_price or charges.completed[0].amount
        Object commObj = payload.get("Commissions");
        if (commObj instanceof Map) {
            Map<String, Object> comm = (Map<String, Object>) commObj;
            Object amt = comm.getOrDefault("my_commission", comm.get("product_base_price"));
            if (amt instanceof Number) {
                // Kiwify geralmente envia em centavos
                req.setPrice(BigDecimal.valueOf(((Number) amt).doubleValue() / 100.0));
            } else if (amt instanceof String) {
                try { req.setPrice(new BigDecimal((String) amt).divide(new BigDecimal(100))); } catch (Exception ignored) {}
            }
            req.setCurrency((String) comm.get("currency"));
        }

        // fallback: charges.completed[0].amount
        if (req.getPrice() == null && payload.get("Subscription") instanceof Map) {
            Map<String, Object> sub = (Map<String, Object>) payload.get("Subscription");
            Object chargesObj = sub.get("charges");
            if (chargesObj instanceof Map) {
                Map<String, Object> charges = (Map<String, Object>) chargesObj;
                Object completedObj = charges.get("completed");
                if (completedObj instanceof Collection) {
                    for (Object it : (Collection<Object>) completedObj) {
                        if (it instanceof Map) {
                            Map<String, Object> ch = (Map<String, Object>) it;
                            Object a = ch.get("amount");
                            if (a instanceof Number) {
                                req.setPrice(BigDecimal.valueOf(((Number) a).doubleValue() / 100.0));
                                break;
                            }
                        }
                    }
                }
            }
        }

        // approved_date
        Object approved = payload.get("approved_date");
        if (approved instanceof String) {
            try { req.setPurchaseDate(Instant.parse(((String) approved).replace(" ", "T") + "Z")); } catch (Exception ignored) {}
        }

        // Validations
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
