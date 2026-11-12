package br.com.devlopes.smartbudget.controller;

import br.com.devlopes.smartbudget.dto.FinancialSummaryResponse;
import br.com.devlopes.smartbudget.dto.TransactionRequest;
import br.com.devlopes.smartbudget.dto.TransactionResponse;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import br.com.devlopes.smartbudget.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transações Financeiras", description = "Endpoints REST tradicionais para auditoria direta e cadastro de transações no H2")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(
            summary = "Listar transações",
            description = "Lista todas as transações armazenadas no banco H2 com filtros opcionais por categoria e tipo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de transações retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetro de filtro inválido")
    })
    public ResponseEntity<List<TransactionResponse>> listTransactions(
            @Parameter(description = "Filtro opcional por categoria")
            @RequestParam(required = false) TransactionCategory category,
            @Parameter(description = "Filtro opcional por tipo (INCOME ou EXPENSE)")
            @RequestParam(required = false) TransactionType type
    ) {
        return ResponseEntity.ok(transactionService.findTransactions(category, type));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Resumo financeiro consolidado",
            description = "Calcula total de receitas, total de despesas, saldo líquido e sinaliza alerta de déficit quando as despesas superam as receitas."
    )
    @ApiResponse(responseCode = "200", description = "Resumo financeiro calculado com sucesso")
    public ResponseEntity<FinancialSummaryResponse> getSummary() {
        return ResponseEntity.ok(transactionService.calculateFinancialSummary());
    }

    @PostMapping
    @Operation(
            summary = "Registrar transação manualmente",
            description = "Cadastra uma transação diretamente via REST aplicando validações de Bean Validation e regras de domínio."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transação registrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Erro de validação sintática nos campos enviados"),
            @ApiResponse(responseCode = "422", description = "Violação de regra de domínio financeira")
    })
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse created = transactionService.registerTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
