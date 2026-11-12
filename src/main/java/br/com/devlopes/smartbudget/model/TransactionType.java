package br.com.devlopes.smartbudget.model;

import br.com.devlopes.smartbudget.exception.InvalidTransactionException;

import java.util.Locale;
import java.util.Map;

public enum TransactionType {
    INCOME,
    EXPENSE;

    private static final Map<String, TransactionType> SYNONYMS = Map.of(
            "RECEITA", INCOME,
            "ENTRADA", INCOME,
            "GANHO", INCOME,
            "DESPESA", EXPENSE,
            "GASTO", EXPENSE,
            "SAIDA", EXPENSE,
            "SAÍDA", EXPENSE
    );

    public static TransactionType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionException("O tipo da transação é obrigatório (INCOME ou EXPENSE).");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        TransactionType mapped = SYNONYMS.get(normalized);
        if (mapped != null) {
            return mapped;
        }
        try {
            return TransactionType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new InvalidTransactionException(
                    "Tipo de transação inválido: '" + value + "'. Valores aceitos: INCOME, EXPENSE.");
        }
    }
}
