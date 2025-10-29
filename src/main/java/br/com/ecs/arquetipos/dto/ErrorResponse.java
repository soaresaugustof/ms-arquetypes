package br.com.ecs.arquetipos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.RequiredArgsConstructor;

// Inclui no JSON apenas os campos que não são nulos.
// Útil para o campo 'retryAfter', que só aparecerá em erros de rate limit.
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@RequiredArgsConstructor // Cria um construtor para os campos marcados como 'final'
public class ErrorResponse {

    private final String error;
    private final String message;
    private final String code;
    private Integer retryAfter; // Campo opcional

    // Construtor adicional para incluir o campo opcional
    public ErrorResponse(String error, String message, String code, Integer retryAfter) {
        this.error = error;
        this.message = message;
        this.code = code;
        this.retryAfter = retryAfter;
    }
}