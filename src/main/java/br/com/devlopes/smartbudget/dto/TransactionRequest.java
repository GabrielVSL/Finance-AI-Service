package br.com.devlopes.smartbudget.dto;

import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransactionRequest(
        @Schema(description = "Descrição da transação", example = "Salário mensal")
        @NotBlank(message = "A descrição da transação não pode ser vazia.")
        String description,

        @Schema(description = "Valor monetário positivo", example = "5500.00")
        @NotNull(message = "O valor da transação é obrigatório.")
        @DecimalMin(value = "0.01", message = "O valor da transação deve ser maior que zero.")
        BigDecimal amount,

        @Schema(description = "Tipo da transação", example = "INCOME")
        @NotNull(message = "O tipo da transação é obrigatório.")
        TransactionType type,

        @Schema(description = "Categoria da transação", example = "SALARY")
        @NotNull(message = "A categoria da transação é obrigatória.")
        TransactionCategory category
) {
}
