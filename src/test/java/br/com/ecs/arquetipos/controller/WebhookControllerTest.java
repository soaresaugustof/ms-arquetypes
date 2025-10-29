package br.com.ecs.arquetipos.controller;

import br.com.ecs.arquetipos.dto.WebhookRequest;
import br.com.ecs.arquetipos.model.Subscriber;
import br.com.ecs.arquetipos.model.Provider;
import br.com.ecs.arquetipos.service.SubscriberService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Deprecated
@SuppressWarnings({"deprecation","unused"})
@WebMvcTest(WebhookController.class)
public class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriberService subscriberService;

    @Test
    void whenValidRequest_thenReturnsCreated() throws Exception {
        Subscriber s = Subscriber.builder()
                .id(1L)
                .name("Fulano")
                .email("fulano@example.com")
                .createdAt(Instant.now())
                .build();

        when(subscriberService.createOrUpdate("Fulano", "fulano@example.com", Provider.HOTMART)).thenReturn(s);

        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fulano\",\"email\":\"fulano@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("fulano@example.com"))
                .andExpect(jsonPath("$.name").value("Fulano"));
    }

    @Test
    void whenInvalidEmail_thenBadRequest() throws Exception {
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fulano\",\"email\":\"invalid-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenMissingName_thenBadRequest() throws Exception {
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fulano@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenHotmartPayload_thenParsedAndSaved() throws Exception {
        String payload = "{\n" +
                "  \"id\": \"1234567890123456789\",\n" +
                "  \"creation_date\": 12345678,\n" +
                "  \"event\": \"PURCHASE_APPROVED\",\n" +
                "  \"version\": \"2.0.0\",\n" +
                "  \"data\": {\n" +
                "    \"product\": {\n" +
                "      \"id\": 213344,\n" +
                "      \"ucode\": \"2e9c43a9-0aeb-48ed-9464-630f845c23af\",\n" +
                "      \"name\": \"Product Name\"\n" +
                "    },\n" +
                "    \"buyer\": {\n" +
                "      \"email\": \"buyer@email.com\",\n" +
                "      \"name\": \"Buyer Name\",\n" +
                "      \"first_name\": \"Buyer\",\n" +
                "      \"last_name\": \"Name\",\n" +
                "      \"checkout_phone\": \"999999999\",\n" +
                "      \"checkout_phone_code\": \"31\",\n" +
                "      \"document\": \"123456789\",\n" +
                "      \"address\": {\n" +
                "        \"zipcode\": \"30150101\",\n" +
                "        \"country\": \"Brasil\",\n" +
                "        \"number\": \"499\",\n" +
                "        \"address\": \"Avenida Assis Chateaubriand\",\n" +
                "        \"city\": \"Belo Horizonte\",\n" +
                "        \"state\": \"MG\",\n" +
                "        \"neighborhood\": \"Floresta\",\n" +
                "        \"complement\": \"a complement\",\n" +
                "        \"country_iso\": \"BR\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"purchase\": {\n" +
                "      \"approved_date\": 1231241434453,\n" +
                "      \"price\": {\n" +
                "        \"value\": 150.6,\n" +
                "        \"currency_value\": \"BRL\"\n" +
                "      },\n" +
                "      \"transaction\": \"HP02316330308193\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

        Subscriber expected = Subscriber.builder()
                .id(42L)
                .name("Buyer Name")
                .email("buyer@email.com")
                .createdAt(Instant.now())
                .build();

        ArgumentCaptor<WebhookRequest> captor = ArgumentCaptor.forClass(WebhookRequest.class);
        when(subscriberService.createOrUpdate(captor.capture(), eq(Provider.HOTMART))).thenReturn(expected);

        mockMvc.perform(post("/webhook/hotmart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("buyer@email.com"))
                .andExpect(jsonPath("$.name").value("Buyer Name"));

        WebhookRequest captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("buyer@email.com", captured.getEmail());
        assertEquals("Buyer Name", captured.getName());
        assertEquals("Buyer", captured.getFirstName());
        assertEquals("Name", captured.getLastName());
        assertEquals("+31 999999999", captured.getPhone());
        assertEquals("123456789", captured.getDocument());
        assertEquals("30150101", captured.getZipcode());
        assertEquals("Belo Horizonte", captured.getCity());
        assertEquals("MG", captured.getState());
        assertEquals("BR", captured.getCountry());
        assertEquals("213344", captured.getProductId());
        assertEquals("Product Name", captured.getProductName());
        assertEquals("HP02316330308193", captured.getTransactionId());
        assertEquals("BRL", captured.getCurrency());
        assertNotNull(captured.getPrice());
        assertEquals(0, captured.getPrice().compareTo(new java.math.BigDecimal("150.6")));
        assertNotNull(captured.getPurchaseDate());
    }
}
