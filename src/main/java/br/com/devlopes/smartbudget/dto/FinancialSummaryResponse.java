package br.com.devlopes.smartbudget.dto;

import java.math.BigDecimal;

public record FinancialSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netBalance,
        boolean deficitAlert,
        String alertMessage,
        long transactionCount
) {
}
