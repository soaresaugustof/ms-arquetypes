package br.com.ecs.arquetipos.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.node.ArrayNode;
import br.com.ecs.arquetipos.repository.SubscriberRepository;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailValidationService {

    private final Optional<HotmartClient> hotmartClient;
    private final SubscriberRepository subscriberRepository;

    public boolean isUserActiveStudent(String email) {
        // Primeiro verifica no nosso banco de dados local
        if (email == null || email.isBlank()) return false;

        if (subscriberRepository.findByEmail(email).isPresent()) {
            return true;
        }

        // Se não houver HotmartClient (ex.: em testes de integração simples), retornamos false
        if (hotmartClient.isEmpty()) return false;

        try {
            JsonNode result = hotmartClient.get().getClubUsers(email).block();

            if (result == null || !result.has("items")) {
                return false;
            }

            ArrayNode users = (ArrayNode) result.get("items");
            if (users.isEmpty()) {
                return false;
            }

            for (JsonNode user : users) {
                if (user.has("status") && "ACTIVE".equals(user.get("status").asText())) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            throw e;
        }
    }
}