package br.com.ecs.arquetipos.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class HotmartAPIException extends RuntimeException {
    private final HttpStatus status;
    private final String responseBody; // Para guardar a resposta original, se houver
    private final Integer retryAfter; // opcional: tempo em segundos sugerido pelo provider

    public HotmartAPIException(String message, HttpStatus status, String responseBody, Integer retryAfter) {
        super(message);
        this.status = status;
        this.responseBody = responseBody;
        this.retryAfter = retryAfter;
    }

    public HotmartAPIException(String message, HttpStatus status, String responseBody) {
        this(message, status, responseBody, null);
    }

    public HotmartAPIException(String message, HttpStatus status) {
        this(message, status, null, null);
    }
}