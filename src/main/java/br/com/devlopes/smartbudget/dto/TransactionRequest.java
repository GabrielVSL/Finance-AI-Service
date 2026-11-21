package br.com.devlopes.smartbudget.dto;

import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransactionRequest(
        @Schema(description = "Descrição da transação", example = "Salário mensal")
        @NotBlank(message = "A descrição da transação não pode ser vazia.")
        @Size(max = 255, message = "A descrição da transação deve ter no máximo 255 caracteres.")
        String description,

        @Schema(description = "Valor monetário positivo", example = "5500.00")
        @NotNull(message = "O valor da transação é obrigatório.")
        @DecimalMin(value = "0.01", message = "O valor da transação deve ser maior que zero.")
        @DecimalMax(value = "10000000.00", message = "O valor máximo permitido por transação é R$ 10.000.000,00.")
        BigDecimal amount,

        @Schema(description = "Tipo da transação", example = "INCOME")
        @NotNull(message = "O tipo da transação é obrigatório.")
        TransactionType type,

        @Schema(description = "Categoria da transação", example = "SALARY")
        @NotNull(message = "A categoria da transação é obrigatória.")
        TransactionCategory category
) {
}
