package br.com.devlopes.smartbudget.service;

import br.com.devlopes.smartbudget.config.SessionContext;
import br.com.devlopes.smartbudget.dto.FinancialSummaryResponse;
import br.com.devlopes.smartbudget.dto.TransactionRequest;
import br.com.devlopes.smartbudget.dto.TransactionResponse;
import br.com.devlopes.smartbudget.model.Transaction;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import br.com.devlopes.smartbudget.repository.TransactionRepository;
import br.com.devlopes.smartbudget.repository.TypeAggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final long retentionHours;

    public TransactionService(
            TransactionRepository transactionRepository,
            @Value("${app.session.retention-hours:24}") long retentionHours
    ) {
        this.transactionRepository = transactionRepository;
        this.retentionHours = retentionHours;
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
        String sessionId = SessionContext.getCurrentSessionId();
        Transaction transaction = new Transaction(sessionId, description, amount, type, category);
        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findTransactions(TransactionCategory category, TransactionType type) {
        String sessionId = SessionContext.getCurrentSessionId();
        List<Transaction> transactions;
        if (category != null && type != null) {
            transactions = transactionRepository.findBySessionIdAndCategoryAndTypeOrderByCreatedAtDesc(
                    sessionId, category, type
            );
        } else if (category != null) {
            transactions = transactionRepository.findBySessionIdAndCategoryOrderByCreatedAtDesc(
                    sessionId, category
            );
        } else if (type != null) {
            transactions = transactionRepository.findBySessionIdAndTypeOrderByCreatedAtDesc(
                    sessionId, type
            );
        } else {
            transactions = transactionRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
        }
        return transactions.stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse calculateFinancialSummary() {
        String sessionId = SessionContext.getCurrentSessionId();
        List<TypeAggregate> aggregates = transactionRepository.summarizeByTypeAndSessionId(sessionId);

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

    @Transactional
    public long clearCurrentSessionTransactions() {
        String sessionId = SessionContext.getCurrentSessionId();
        return transactionRepository.deleteBySessionId(sessionId);
    }

    @Scheduled(fixedRateString = "${app.session.cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanupExpiredTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retentionHours);
        long deleted = transactionRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Limpeza automática H2: {} transação(ões) com mais de {}h removida(s).", deleted, retentionHours);
        }
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
