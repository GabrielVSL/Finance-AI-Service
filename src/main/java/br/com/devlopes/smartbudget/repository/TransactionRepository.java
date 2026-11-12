package br.com.devlopes.smartbudget.repository;

import br.com.devlopes.smartbudget.model.Transaction;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findAllByOrderByCreatedAtDesc();

    List<Transaction> findByCategoryOrderByCreatedAtDesc(TransactionCategory category);

    List<Transaction> findByTypeOrderByCreatedAtDesc(TransactionType type);

    List<Transaction> findByCategoryAndTypeOrderByCreatedAtDesc(
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
            GROUP BY t.type
            """)
    List<TypeAggregate> summarizeByType();
}
