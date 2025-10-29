package br.com.ecs.arquetipos.service;

import br.com.ecs.arquetipos.model.Subscriber;
import br.com.ecs.arquetipos.model.Provider;
import br.com.ecs.arquetipos.repository.SubscriberRepository;
import br.com.ecs.arquetipos.dto.WebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriberService {

    private final SubscriberRepository repository;

    // Mantém método antigo por compatibilidade (delegando)
    public Subscriber createOrUpdate(String name, String email, Provider provider) {
        WebhookRequest req = new WebhookRequest();
        req.setName(name);
        req.setEmail(email);
        return createOrUpdate(req, provider);
    }

    public Subscriber createOrUpdate(WebhookRequest req, Provider provider) {
        String email = req.getEmail();
        Optional<Subscriber> existing = repository.findByEmail(email);
        if (existing.isPresent()) {
            Subscriber s = existing.get();
            boolean changed = false;
            if (req.getName() != null && !req.getName().equals(s.getName())) {
                log.info("Atualizando nome do subscriber (email={}): {} -> {}", email, s.getName(), req.getName());
                s.setName(req.getName());
                changed = true;
            }
            if (req.getFirstName() != null && !req.getFirstName().equals(s.getFirstName())) {
                s.setFirstName(req.getFirstName()); changed = true;
            }
            if (req.getLastName() != null && !req.getLastName().equals(s.getLastName())) {
                s.setLastName(req.getLastName()); changed = true;
            }
            if (req.getPhone() != null && !req.getPhone().equals(s.getPhone())) { s.setPhone(req.getPhone()); changed = true; }
            if (req.getDocument() != null && !req.getDocument().equals(s.getDocument())) { s.setDocument(req.getDocument()); changed = true; }
            if (req.getZipcode() != null && !req.getZipcode().equals(s.getZipcode())) { s.setZipcode(req.getZipcode()); changed = true; }
            if (req.getCity() != null && !req.getCity().equals(s.getCity())) { s.setCity(req.getCity()); changed = true; }
            if (req.getState() != null && !req.getState().equals(s.getState())) { s.setState(req.getState()); changed = true; }
            if (req.getCountry() != null && !req.getCountry().equals(s.getCountry())) { s.setCountry(req.getCountry()); changed = true; }

            if (req.getProductId() != null && !req.getProductId().equals(s.getProductId())) { s.setProductId(req.getProductId()); changed = true; }
            if (req.getProductName() != null && !req.getProductName().equals(s.getProductName())) { s.setProductName(req.getProductName()); changed = true; }
            if (req.getTransactionId() != null && !req.getTransactionId().equals(s.getTransactionId())) { s.setTransactionId(req.getTransactionId()); changed = true; }
            if (req.getPrice() != null && (s.getPrice() == null || req.getPrice().compareTo(s.getPrice()) != 0)) { s.setPrice(req.getPrice()); changed = true; }
            if (req.getCurrency() != null && !req.getCurrency().equals(s.getCurrency())) { s.setCurrency(req.getCurrency()); changed = true; }
            if (req.getPurchaseDate() != null && !req.getPurchaseDate().equals(s.getPurchaseDate())) { s.setPurchaseDate(req.getPurchaseDate()); changed = true; }

            if (s.getProvider() != provider) {
                s.setProvider(provider);
                changed = true;
            }
            if (changed) {
                log.info("Atualizando subscriber existente: {}", email);
                try {
                    return repository.save(s);
                } catch (Exception ex) {
                    // Tenta recuperar registro existente em caso de violação de integridade ou condição de corrida
                    log.warn("Erro ao salvar atualização do subscriber (email={}), tentando recuperar registro existente: {}", email, ex.getMessage());
                    return repository.findByEmail(email).orElseThrow(() -> unwrapOrRethrow(ex));
                }
            }
            log.info("Retornando subscriber existente sem alterações: {}", email);
            return s;
        }

        Subscriber subscriber = Subscriber.builder()
                .name(req.getName())
                .email(req.getEmail())
                .createdAt(Instant.now())
                .provider(provider)
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .phone(req.getPhone())
                .document(req.getDocument())
                .zipcode(req.getZipcode())
                .city(req.getCity())
                .state(req.getState())
                .country(req.getCountry())
                .productId(req.getProductId())
                .productName(req.getProductName())
                .transactionId(req.getTransactionId())
                .price(req.getPrice())
                .currency(req.getCurrency())
                .purchaseDate(req.getPurchaseDate())
                .build();

        // Revalida presença antes de tentar inserir para evitar condição de corrida
        Optional<Subscriber> already = repository.findByEmail(email);
        if (already.isPresent()) {
            log.info("Subscriber encontrado na segunda checagem, retornando existente: {}", email);
            return already.get();
        }

        try {
            log.info("Criando novo subscriber: {}", email);
            return repository.save(subscriber);
        } catch (Exception ex) {
            // Tenta recuperar registro existente em caso de violação de integridade ou condição de corrida
            log.warn("Erro ao salvar novo subscriber (email={}), tentando recuperar registro existente: {}", email, ex.getMessage());
            return repository.findByEmail(email).orElseThrow(() -> unwrapOrRethrow(ex));
        }
    }

    private RuntimeException unwrapOrRethrow(Exception ex) {
        if (ex instanceof RuntimeException) {
            return (RuntimeException) ex;
        }
        return new RuntimeException(ex);
    }
}
