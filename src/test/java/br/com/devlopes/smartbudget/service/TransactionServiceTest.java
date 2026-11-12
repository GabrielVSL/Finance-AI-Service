package br.com.devlopes.smartbudget.service;

import br.com.devlopes.smartbudget.dto.FinancialSummaryResponse;
import br.com.devlopes.smartbudget.dto.TransactionResponse;
import br.com.devlopes.smartbudget.exception.InvalidTransactionException;
import br.com.devlopes.smartbudget.model.Transaction;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import br.com.devlopes.smartbudget.repository.TransactionRepository;
import br.com.devlopes.smartbudget.repository.TypeAggregate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    @DisplayName("Deve salvar transação válida com escala monetária padronizada")
    void shouldSaveValidTransaction() {
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.registerTransaction(
                "  Assinatura Cloud  ",
                new BigDecimal("99.9"),
                TransactionType.EXPENSE,
                TransactionCategory.TECH
        );

        assertThat(response.description()).isEqualTo("Assinatura Cloud");
        assertThat(response.amount()).isEqualByComparingTo("99.90");
        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.category()).isEqualTo(TransactionCategory.TECH);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Não deve chamar repositório quando valor for negativo ou zero")
    void shouldNotSaveWhenAmountIsZeroOrNegative() {
        assertThatThrownBy(() -> transactionService.registerTransaction(
                "Teste",
                BigDecimal.ZERO,
                TransactionType.EXPENSE,
                TransactionCategory.OTHER
        )).isInstanceOf(InvalidTransactionException.class);

        assertThatThrownBy(() -> transactionService.registerTransaction(
                "Teste",
                new BigDecimal("-10.00"),
                TransactionType.INCOME,
                TransactionCategory.SALARY
        )).isInstanceOf(InvalidTransactionException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve calcular saldo líquido empatado sem alerta de déficit a partir de agregação SQL")
    void shouldHandleBalancedBudgetSummary() {
        when(transactionRepository.summarizeByType()).thenReturn(List.of(
                new TypeAggregate(TransactionType.INCOME, new BigDecimal("2000.00"), 1L),
                new TypeAggregate(TransactionType.EXPENSE, new BigDecimal("2000.00"), 1L)
        ));

        FinancialSummaryResponse summary = transactionService.calculateFinancialSummary();

        assertThat(summary.netBalance()).isEqualByComparingTo("0.00");
        assertThat(summary.deficitAlert()).isFalse();
        assertThat(summary.transactionCount()).isEqualTo(2L);
        assertThat(summary.alertMessage()).contains("equilibrado");
    }
}
