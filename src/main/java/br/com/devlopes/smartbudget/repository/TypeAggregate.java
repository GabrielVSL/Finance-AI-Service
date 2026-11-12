package br.com.devlopes.smartbudget.repository;

import br.com.devlopes.smartbudget.model.TransactionType;

import java.math.BigDecimal;

public record TypeAggregate(TransactionType type, BigDecimal totalAmount, long count) {
}
