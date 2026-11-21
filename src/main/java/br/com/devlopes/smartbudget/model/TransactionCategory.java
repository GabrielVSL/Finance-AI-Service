package br.com.devlopes.smartbudget.model;

import br.com.devlopes.smartbudget.exception.InvalidTransactionException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public enum TransactionCategory {
    FOOD,
    TRANSPORT,
    SALARY,
    TECH,
    HOUSING,
    HEALTH,
    ENTERTAINMENT,
    OTHER;

    private static final Map<String, TransactionCategory> SYNONYMS = Map.ofEntries(
            Map.entry("ALIMENTACAO", FOOD),
            Map.entry("ALIMENTAÇÃO", FOOD),
            Map.entry("COMIDA", FOOD),
            Map.entry("TRANSPORTE", TRANSPORT),
            Map.entry("SALARIO", SALARY),
            Map.entry("SALÁRIO", SALARY),
            Map.entry("TECNOLOGIA", TECH),
            Map.entry("MORADIA", HOUSING),
            Map.entry("CASA", HOUSING),
            Map.entry("SAUDE", HEALTH),
            Map.entry("SAÚDE", HEALTH),
            Map.entry("LAZER", ENTERTAINMENT),
            Map.entry("ENTRETENIMENTO", ENTERTAINMENT),
            Map.entry("OUTRO", OTHER),
            Map.entry("OUTROS", OTHER)
    );

    public static TransactionCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionException("A categoria da transação é obrigatória.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        TransactionCategory mapped = SYNONYMS.get(normalized);
        if (mapped != null) {
            return mapped;
        }
        try {
            return TransactionCategory.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            String allowed = Arrays.stream(values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            String safeValue = value.trim().replaceAll("[\\r\\n\\t]", " ");
            if (safeValue.length() > 32) {
                safeValue = safeValue.substring(0, 32) + "...";
            }
            throw new InvalidTransactionException(
                    "Categoria inválida: '" + safeValue + "'. Valores aceitos: " + allowed + ".");
        }
    }
}
