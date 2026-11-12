package br.com.devlopes.smartbudget.tools;

import br.com.devlopes.smartbudget.dto.FinancialSummaryResponse;
import br.com.devlopes.smartbudget.dto.TransactionResponse;
import br.com.devlopes.smartbudget.exception.InvalidTransactionException;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import br.com.devlopes.smartbudget.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FinancialToolsIntegrationTest {

    @Autowired
    private FinancialTools financialTools;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve registrar receita e despesa via FinancialTools e persistir no H2")
    void shouldRegisterIncomeAndExpenseViaTool() {
        TransactionResponse income = financialTools.registrarTransacao(
                "Salário mensal",
                new BigDecimal("5000.00"),
                "INCOME",
                "SALARY"
        );
        TransactionResponse expense = financialTools.registrarTransacao(
                "Notebook para estudos",
                new BigDecimal("3200.50"),
                "EXPENSE",
                "TECH"
        );

        assertThat(income.id()).isNotNull();
        assertThat(income.type()).isEqualTo(TransactionType.INCOME);
        assertThat(income.category()).isEqualTo(TransactionCategory.SALARY);

        assertThat(expense.id()).isNotNull();
        assertThat(expense.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(expense.amount()).isEqualByComparingTo("3200.50");
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve aceitar sinônimos em português para tipo e categoria via Tool")
    void shouldAcceptPortugueseSynonymsViaTool() {
        TransactionResponse response = financialTools.registrarTransacao(
                "Supermercado",
                new BigDecimal("210.00"),
                "despesa",
                "alimentação"
        );

        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.category()).isEqualTo(TransactionCategory.FOOD);
    }

    @Test
    @DisplayName("Deve impedir registro com valor menor ou igual a zero ou descrição vazia via Tool")
    void shouldRejectInvalidTransactionsViaTool() {
        assertThatThrownBy(() -> financialTools.registrarTransacao(
                "Café",
                BigDecimal.ZERO,
                "EXPENSE",
                "FOOD"
        )).isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("maior que zero");

        assertThatThrownBy(() -> financialTools.registrarTransacao(
                "Compra inválida",
                new BigDecimal("-50.00"),
                "EXPENSE",
                "FOOD"
        )).isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("maior que zero");

        assertThatThrownBy(() -> financialTools.registrarTransacao(
                "   ",
                new BigDecimal("25.00"),
                "EXPENSE",
                "FOOD"
        )).isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("descrição");
    }

    @Test
    @DisplayName("Deve filtrar transações por categoria e por tipo via Tool")
    void shouldFilterTransactionsByCategoryAndType() {
        financialTools.registrarTransacao("Almoço", new BigDecimal("45.00"), "EXPENSE", "FOOD");
        financialTools.registrarTransacao("Uber", new BigDecimal("32.00"), "EXPENSE", "TRANSPORT");
        financialTools.registrarTransacao("Reembolso alimentação", new BigDecimal("100.00"), "INCOME", "FOOD");

        List<TransactionResponse> foodAll = financialTools.listarTransacoesPorCategoria("FOOD", null);
        List<TransactionResponse> foodExpenses = financialTools.listarTransacoesPorCategoria("FOOD", "EXPENSE");
        List<TransactionResponse> all = financialTools.listarTransacoesPorCategoria(null, null);

        assertThat(foodAll).hasSize(2);
        assertThat(foodExpenses).hasSize(1);
        assertThat(foodExpenses.getFirst().description()).isEqualTo("Almoço");
        assertThat(all).hasSize(3);
    }

    @Test
    @DisplayName("Deve calcular resumo financeiro superavitário sem alerta de déficit")
    void shouldCalculatePositiveFinancialSummary() {
        financialTools.registrarTransacao("Salário", new BigDecimal("4000.00"), "INCOME", "SALARY");
        financialTools.registrarTransacao("Mercado", new BigDecimal("1250.00"), "EXPENSE", "FOOD");

        FinancialSummaryResponse summary = financialTools.calcularResumoFinanceiro();

        assertThat(summary.totalIncome()).isEqualByComparingTo("4000.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("1250.00");
        assertThat(summary.netBalance()).isEqualByComparingTo("2750.00");
        assertThat(summary.deficitAlert()).isFalse();
        assertThat(summary.transactionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve disparar alerta de déficit quando despesas superam receitas")
    void shouldTriggerDeficitAlertWhenExpensesExceedIncome() {
        financialTools.registrarTransacao("Freelance", new BigDecimal("1000.00"), "INCOME", "OTHER");
        financialTools.registrarTransacao("Aluguel", new BigDecimal("1800.00"), "EXPENSE", "HOUSING");

        FinancialSummaryResponse summary = financialTools.calcularResumoFinanceiro();

        assertThat(summary.totalIncome()).isEqualByComparingTo("1000.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("1800.00");
        assertThat(summary.netBalance()).isEqualByComparingTo("-800.00");
        assertThat(summary.deficitAlert()).isTrue();
        assertThat(summary.alertMessage()).contains("ALERTA DE DÉFICIT");
    }
}
