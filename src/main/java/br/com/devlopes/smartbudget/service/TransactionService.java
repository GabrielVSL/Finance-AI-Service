package br.com.devlopes.smartbudget.service;

import br.com.devlopes.smartbudget.config.SessionContext;
import br.com.devlopes.smartbudget.dto.FinancialSummaryResponse;
import br.com.devlopes.smartbudget.dto.TransactionRequest;
import br.com.devlopes.smartbudget.dto.TransactionResponse;
import br.com.devlopes.smartbudget.exception.InvalidTransactionException;
import br.com.devlopes.smartbudget.model.Transaction;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import br.com.devlopes.smartbudget.repository.TransactionRepository;
import br.com.devlopes.smartbudget.repository.TypeAggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final long DEFAULT_MAX_PER_SESSION = 200L;
    private static final long DEFAULT_MAX_TOTAL = 5000L;

    private final TransactionRepository transactionRepository;
    private final long retentionHours;
    private final long maxTransactionsPerSession;
    private final long maxTotalTransactions;

    @Autowired
    public TransactionService(
            TransactionRepository transactionRepository,
            @Value("${app.session.retention-hours:24}") long retentionHours,
            @Value("${app.session.max-transactions-per-session:200}") long maxTransactionsPerSession,
            @Value("${app.session.max-total-transactions:5000}") long maxTotalTransactions
    ) {
        this.transactionRepository = transactionRepository;
        this.retentionHours = retentionHours;
        this.maxTransactionsPerSession = Math.max(1L, maxTransactionsPerSession);
        this.maxTotalTransactions = Math.max(1L, maxTotalTransactions);
    }

    TransactionService(TransactionRepository transactionRepository, long retentionHours) {
        this(transactionRepository, retentionHours, DEFAULT_MAX_PER_SESSION, DEFAULT_MAX_TOTAL);
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
        if (transactionRepository.countBySessionId(sessionId) >= maxTransactionsPerSession) {
            throw new InvalidTransactionException(
                    "Limite máximo de " + maxTransactionsPerSession + " transações por sessão atingido.");
        }
        if (transactionRepository.count() >= maxTotalTransactions) {
            throw new InvalidTransactionException(
                    "Capacidade temporária de armazenamento atingida. Tente novamente mais tarde.");
        }
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
