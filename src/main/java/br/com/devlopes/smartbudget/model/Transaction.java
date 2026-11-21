package br.com.devlopes.smartbudget.model;

import br.com.devlopes.smartbudget.config.SessionContext;
import br.com.devlopes.smartbudget.exception.InvalidTransactionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_session_created", columnList = "session_id, created_at")
        }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionCategory category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Transaction() {
        // Requerido pelo JPA
    }

    public Transaction(String description, BigDecimal amount, TransactionType type, TransactionCategory category) {
        this(SessionContext.getCurrentSessionId(), description, amount, type, category);
    }

    public Transaction(
            String sessionId,
            String description,
            BigDecimal amount,
            TransactionType type,
            TransactionCategory category
    ) {
        validate(description, amount, type, category);
        this.sessionId = SessionContext.sanitize(sessionId);
        this.description = description.trim();
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.type = type;
        this.category = category;
        this.createdAt = LocalDateTime.now();
    }

    public static final int MAX_DESCRIPTION_LENGTH = 255;
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000.00");

    public static void validate(
            String description,
            BigDecimal amount,
            TransactionType type,
            TransactionCategory category
    ) {
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("A descrição da transação não pode ser vazia.");
        }
        if (description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidTransactionException(
                    "A descrição da transação deve ter no máximo " + MAX_DESCRIPTION_LENGTH + " caracteres.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("O valor da transação deve ser maior que zero.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidTransactionException("O valor máximo permitido por transação é R$ 10.000.000,00.");
        }
        if (type == null) {
            throw new InvalidTransactionException("O tipo da transação (INCOME ou EXPENSE) é obrigatório.");
        }
        if (category == null) {
            throw new InvalidTransactionException("A categoria da transação é obrigatória.");
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.sessionId == null || this.sessionId.isBlank()) {
            this.sessionId = SessionContext.DEFAULT_SESSION_ID;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transaction that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
