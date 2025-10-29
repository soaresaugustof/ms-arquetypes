package br.com.ecs.arquetipos;

import br.com.ecs.arquetipos.controller.ValidationController;
import br.com.ecs.arquetipos.dto.EmailRequest;
import br.com.ecs.arquetipos.exception.HotmartAPIException;
import br.com.ecs.arquetipos.service.EmailValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean; // 1. Importar @MockBean
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// A anotação @WebMvcTest continua a mesma
@WebMvcTest(ValidationController.class)
class ValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 2. Usar @MockBean para adicionar um mock do serviço ao contexto do teste.
    //    Isso resolve o erro "No qualifying bean... available".
    @MockBean
    private EmailValidationService emailValidationService;

    // 3. O construtor personalizado foi removido, voltando ao padrão.

    @Test
    void whenValidEmailAndUserIsStudent_thenReturnsSuccessWithTrue() throws Exception {
        // GIVEN
        String userEmail = "aluno@hotmart.com";
        EmailRequest request = new EmailRequest();
        request.setEmail(userEmail);

        when(emailValidationService.isUserActiveStudent(userEmail)).thenReturn(true);

        // WHEN & THEN
        mockMvc.perform(post("/validate-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_student").value(true));
    }

    // ... resto dos testes (eles não precisam de alteração) ...

    @Test
    void whenServiceThrowsApiError_thenReturnsErrorResponse() throws Exception {
        // GIVEN
        String userEmail = "erro@api.com";
        EmailRequest request = new EmailRequest();
        request.setEmail(userEmail);

        when(emailValidationService.isUserActiveStudent(anyString()))
                .thenThrow(new HotmartAPIException("Erro no servidor da Hotmart", HttpStatus.INTERNAL_SERVER_ERROR));

        // WHEN & THEN
        mockMvc.perform(post("/validate-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("HOTMART_API_ERROR"));
    }
}