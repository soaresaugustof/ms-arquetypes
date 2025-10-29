package br.com.ecs.arquetipos.service;

import br.com.ecs.arquetipos.model.Subscriber;
import br.com.ecs.arquetipos.model.Provider;
import br.com.ecs.arquetipos.repository.SubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriberServiceTest {

    @Mock
    private SubscriberRepository repository;

    @InjectMocks
    private SubscriberService subscriberService;

    private final String email = "fulano@example.com";

    @BeforeEach
    void setup() {
    }

    @Test
    void createNewSubscriber_whenNotExists() {
        when(repository.findByEmail(email)).thenReturn(Optional.empty());

        Subscriber toSave = Subscriber.builder()
                .id(1L)
                .name("Fulano")
                .email(email)
                .createdAt(Instant.now())
                .build();

        when(repository.save(any(Subscriber.class))).thenReturn(toSave);

        Subscriber result = subscriberService.createOrUpdate("Fulano", email, Provider.HOTMART);

        assertNotNull(result);
        assertEquals(email, result.getEmail());
        assertEquals("Fulano", result.getName());
        verify(repository, times(1)).save(any(Subscriber.class));
    }

    @Test
    void updateExistingSubscriber_whenNameChanged() {
        Subscriber existing = Subscriber.builder()
                .id(2L)
                .name("Old")
                .email(email)
                .createdAt(Instant.now())
                .build();

        when(repository.findByEmail(email)).thenReturn(Optional.of(existing));

        Subscriber updated = Subscriber.builder()
                .id(2L)
                .name("Novo")
                .email(email)
                .createdAt(existing.getCreatedAt())
                .build();

        when(repository.save(existing)).thenReturn(updated);

        Subscriber result = subscriberService.createOrUpdate("Novo", email, Provider.EDUZZ);

        assertNotNull(result);
        assertEquals("Novo", result.getName());
        verify(repository, times(1)).save(existing);
    }

    @Test
    void handleDataIntegrityViolationByReturningExisting() {
        when(repository.findByEmail(email)).thenReturn(Optional.empty());

        // Simula salvamento lançando DataIntegrityViolationException (condição de corrida)
        when(repository.save(any(Subscriber.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        Subscriber existing = Subscriber.builder()
                .id(3L)
                .name("Exists")
                .email(email)
                .createdAt(Instant.now())
                .build();

        when(repository.findByEmail(email)).thenReturn(Optional.of(existing));

        Subscriber result = subscriberService.createOrUpdate("Exists", email, Provider.HOTMART);

        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        assertEquals(existing.getEmail(), result.getEmail());
        verify(repository, atLeastOnce()).findByEmail(email);
    }
}
