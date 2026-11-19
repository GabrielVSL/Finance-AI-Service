package br.com.devlopes.smartbudget.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@Tag(name = "Monitoramento", description = "Endpoint leve para health check e keep-alive em hospedagem gratuita")
public class HealthController {

    @GetMapping("/health")
    @Operation(
            summary = "Verificar status da aplicação",
            description = "Retorna 200 OK com status UP. Ideal para pings periódicos (UptimeRobot / Cron-job.org)."
    )
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "smart-budget-ai", LocalDateTime.now()));
    }

    public record HealthResponse(String status, String service, LocalDateTime timestamp) {
    }
}
