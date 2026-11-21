package br.com.devlopes.smartbudget.tools;

import br.com.devlopes.smartbudget.dto.FinancialSummaryResponse;
import br.com.devlopes.smartbudget.dto.TransactionResponse;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import br.com.devlopes.smartbudget.service.TransactionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class FinancialTools {

    private final TransactionService transactionService;

    public FinancialTools(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Tool(description = """
            Registra uma nova transação financeira (receita ou despesa) no banco de dados H2 após validar \
            descrição não vazia, valor estritamente positivo (> 0), tipo e categoria. \
            Use sempre que o usuário informar que ganhou, recebeu, gastou, pagou ou comprou algo.
            """)
    public TransactionResponse registrarTransacao(
            @ToolParam(description = "Descrição curta e clara da transação, ex: 'Almoço executivo' ou 'Salário mensal'")
            String description,
            @ToolParam(description = "Valor monetário positivo maior que zero (ex: 85.50). Valores <= 0 são rejeitados.")
            BigDecimal amount,
            @ToolParam(description = "Tipo da transação: 'INCOME' (receita/ganho) ou 'EXPENSE' (despesa/gasto)")
            String type,
            @ToolParam(description = "Categoria exata: 'FOOD', 'TRANSPORT', 'SALARY', 'TECH', 'HOUSING', 'HEALTH', 'ENTERTAINMENT' ou 'OTHER'")
            String category
    ) {
        TransactionType parsedType = TransactionType.fromString(type);
        TransactionCategory parsedCategory = TransactionCategory.fromString(category);
        return transactionService.registerTransaction(description, amount, parsedType, parsedCategory);
    }

    @Tool(description = """
            Lista as transações financeiras cadastradas no banco de dados H2, permitindo filtrar opcionalmente \
            por categoria (FOOD, TRANSPORT, SALARY, TECH, HOUSING, HEALTH, ENTERTAINMENT, OTHER) e/ou por tipo \
            (INCOME ou EXPENSE). Se ambos os filtros forem omitidos, retorna todas as transações.
            """)
    public List<TransactionResponse> listarTransacoesPorCategoria(
            @ToolParam(required = false, description = "Categoria opcional para filtro: FOOD, TRANSPORT, SALARY, TECH, HOUSING, HEALTH, ENTERTAINMENT ou OTHER")
            String category,
            @ToolParam(required = false, description = "Tipo opcional para filtro: INCOME ou EXPENSE")
            String type
    ) {
        TransactionCategory parsedCategory = isProvided(category)
                ? TransactionCategory.fromString(category)
                : null;
        TransactionType parsedType = isProvided(type)
                ? TransactionType.fromString(type)
                : null;
        List<TransactionResponse> results = transactionService.findTransactions(parsedCategory, parsedType);
        return results.size() > 50 ? results.subList(0, 50) : results;
    }

    @Tool(description = """
            Calcula o resumo financeiro consolidado atual no banco H2: soma total de receitas (totalIncome), \
            soma total de despesas (totalExpense), saldo líquido (netBalance), quantidade de lançamentos e \
            alerta de déficit (deficitAlert = true quando despesas superam receitas).
            """)
    public FinancialSummaryResponse calcularResumoFinanceiro() {
        return transactionService.calculateFinancialSummary();
    }

    private boolean isProvided(String value) {
        return value != null && !value.isBlank();
    }
}
