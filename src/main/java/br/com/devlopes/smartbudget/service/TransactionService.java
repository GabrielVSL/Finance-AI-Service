package br.com.devlopes.smartbudget.service;

import br.com.devlopes.smartbudget.dto.FinancialSummaryResponse;
import br.com.devlopes.smartbudget.dto.TransactionRequest;
import br.com.devlopes.smartbudget.dto.TransactionResponse;
import br.com.devlopes.smartbudget.model.Transaction;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import br.com.devlopes.smartbudget.repository.TransactionRepository;
import br.com.devlopes.smartbudget.repository.TypeAggregate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse registerTransaction(TransactionRequest request) {
        return registerTransaction(
                request.description(),
                request.amount(),
                request.type(),
                request.category()
        );
    }

    @Transactional
    public TransactionResponse registerTransaction(
            String description,
            BigDecimal amount,
            TransactionType type,
            TransactionCategory category
    ) {
        Transaction transaction = new Transaction(description, amount, type, category);
        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findTransactions(TransactionCategory category, TransactionType type) {
        List<Transaction> transactions;
        if (category != null && type != null) {
            transactions = transactionRepository.findByCategoryAndTypeOrderByCreatedAtDesc(category, type);
        } else if (category != null) {
            transactions = transactionRepository.findByCategoryOrderByCreatedAtDesc(category);
        } else if (type != null) {
            transactions = transactionRepository.findByTypeOrderByCreatedAtDesc(type);
        } else {
            transactions = transactionRepository.findAllByOrderByCreatedAtDesc();
        }
        return transactions.stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse calculateFinancialSummary() {
        List<TypeAggregate> aggregates = transactionRepository.summarizeByType();

        BigDecimal totalIncome = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalExpense = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        long transactionCount = 0L;

        for (TypeAggregate aggregate : aggregates) {
            transactionCount += aggregate.count();
            BigDecimal sum = aggregate.totalAmount() != null
                    ? aggregate.totalAmount().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            if (aggregate.type() == TransactionType.INCOME) {
                totalIncome = sum;
            } else if (aggregate.type() == TransactionType.EXPENSE) {
                totalExpense = sum;
            }
        }

        BigDecimal netBalance = totalIncome.subtract(totalExpense).setScale(2, RoundingMode.HALF_UP);
        boolean deficitAlert = totalExpense.compareTo(totalIncome) > 0;
        String alertMessage = buildAlertMessage(deficitAlert, netBalance);

        return new FinancialSummaryResponse(
                totalIncome,
                totalExpense,
                netBalance,
                deficitAlert,
                alertMessage,
                transactionCount
        );
    }

    private String buildAlertMessage(boolean deficitAlert, BigDecimal netBalance) {
        if (deficitAlert) {
            return "ALERTA DE DÉFICIT: Suas despesas superaram as receitas. Saldo líquido atual: R$ " + netBalance;
        }
        if (netBalance.compareTo(BigDecimal.ZERO) == 0) {
            return "Orçamento equilibrado: suas receitas e despesas estão empatadas.";
        }
        return "Saúde financeira positiva: saldo líquido superavitário de R$ " + netBalance;
    }
}
