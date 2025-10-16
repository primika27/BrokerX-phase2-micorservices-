package com.broker.orderService.infrastructure.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.broker.orderService.domain.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

}
