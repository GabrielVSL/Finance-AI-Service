package br.com.devlopes.smartbudget.model;

import br.com.devlopes.smartbudget.exception.InvalidTransactionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
        validate(description, amount, type, category);
        this.description = description.trim();
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.type = type;
        this.category = category;
        this.createdAt = LocalDateTime.now();
    }

    public static void validate(
            String description,
            BigDecimal amount,
            TransactionType type,
            TransactionCategory category
    ) {
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("A descrição da transação não pode ser vazia.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("O valor da transação deve ser maior que zero.");
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
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
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
