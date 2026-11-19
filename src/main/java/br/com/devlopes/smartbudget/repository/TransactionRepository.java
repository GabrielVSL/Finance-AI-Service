package br.com.devlopes.smartbudget.repository;

import br.com.devlopes.smartbudget.model.Transaction;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<Transaction> findBySessionIdAndCategoryOrderByCreatedAtDesc(
            String sessionId,
            TransactionCategory category
    );

    List<Transaction> findBySessionIdAndTypeOrderByCreatedAtDesc(
            String sessionId,
            TransactionType type
    );

    List<Transaction> findBySessionIdAndCategoryAndTypeOrderByCreatedAtDesc(
            String sessionId,
            TransactionCategory category,
            TransactionType type
    );

    @Query("""
            SELECT new br.com.devlopes.smartbudget.repository.TypeAggregate(
                t.type,
                COALESCE(SUM(t.amount), 0),
                COUNT(t)
            )
            FROM Transaction t
            WHERE t.sessionId = :sessionId
            GROUP BY t.type
            """)
    List<TypeAggregate> summarizeByTypeAndSessionId(@Param("sessionId") String sessionId);

    long deleteBySessionId(String sessionId);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
