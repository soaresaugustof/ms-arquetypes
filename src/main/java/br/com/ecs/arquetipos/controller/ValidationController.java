package br.com.ecs.arquetipos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import br.com.ecs.arquetipos.dto.EmailRequest;
import br.com.ecs.arquetipos.service.EmailValidationService;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class ValidationController {

    private final EmailValidationService emailValidationService;

    // Este controller não realiza mais consultas à Hotmart.
    // O endpoint /webhook para receber nome/email está em WebhookController.

    // O endpoint /health é provido pelo Spring Boot Actuator,
    // então esta rota customizada não é estritamente necessária.
    // Mas, se quiser replicar exatamente, pode fazer assim:
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "hotmart-email-validator"
        ));
    }

    @PostMapping("/validate-email")
    public ResponseEntity<Map<String, Object>> validateEmail(@Valid @RequestBody EmailRequest request) {
        boolean isStudent = emailValidationService.isUserActiveStudent(request.getEmail());
        Map<String, Object> body = Map.of(
                "email", request.getEmail(),
                "is_student", isStudent,
                "status", "success"
        );
        return ResponseEntity.ok(body);
    }
}