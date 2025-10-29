package br.com.ecs.arquetipos.exception;

import br.com.ecs.arquetipos.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(HotmartAPIException.class)
    public ResponseEntity<ErrorResponse> handleHotmartAPIException(HotmartAPIException ex) {
        log.error("Hotmart API Error: {} - Status: {}", ex.getMessage(), ex.getStatus());

        HttpStatus status = ex.getStatus();
        String code;
        String message;

        if (status == HttpStatus.UNAUTHORIZED) {
            code = "AUTH_ERROR";
            message = "Erro de autenticação com Hotmart";
        } else if (status == HttpStatus.FORBIDDEN) {
            code = "ACCESS_DENIED";
            message = "Acesso negado. Verifique permissões";
        } else if (status == HttpStatus.TOO_MANY_REQUESTS) {
            code = "RATE_LIMIT";
            message = "Muitas tentativas. Aguarde um momento e tente novamente";
            Integer retryAfter = ex.getRetryAfter() != null ? ex.getRetryAfter() : 60;
            ErrorResponse body = new ErrorResponse(message, ex.getMessage(), code, retryAfter);
            return new ResponseEntity<>(body, status);
        } else if (status.is5xxServerError()) {
            // manter um código específico de Hotmart para testes que esperam este código
            code = "HOTMART_API_ERROR";
            message = "Erro interno do servidor. Tente novamente em alguns minutos.";
        } else {
            code = "HOTMART_API_ERROR";
            message = "Hotmart API error";
        }

        ErrorResponse body = new ErrorResponse(message, ex.getMessage(), code);
        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("Erro de formato de dados", "Content-Type inválido", "INVALID_CONTENT_TYPE");
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Invalid JSON: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("Erro nos dados enviados", "JSON inválido ou estrutura inesperada", "INVALID_JSON");
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldError().getField();
        String errorMessage = ex.getBindingResult().getFieldError().getDefaultMessage();

        String code;
        String message;

        if ("email".equalsIgnoreCase(field)) {
            code = "INVALID_EMAIL";
            message = "Formato de email inválido";
        } else {
            code = "INVALID_REQUEST";
            message = errorMessage != null ? errorMessage : "Invalid request data";
        }

        ErrorResponse body = new ErrorResponse(message, errorMessage, code);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        String msg = ex.getMessage() != null ? ex.getMessage() : "Invalid request";
        String code = "INVALID_REQUEST";
        if (msg.contains("Email not found") || msg.contains("Invalid email")) {
            code = "INVALID_EMAIL";
            msg = "Formato de email inválido";
        } else if (msg.contains("Name is required")) {
            code = "INVALID_REQUEST";
            msg = "Nome é obrigatório";
        }
        ErrorResponse body = new ErrorResponse(msg, ex.getMessage(), code);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        log.error("Unexpected error: ", ex);
        ErrorResponse body = new ErrorResponse(
                "Erro interno do servidor",
                "An unexpected error occurred",
                "INTERNAL_ERROR"
        );
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}