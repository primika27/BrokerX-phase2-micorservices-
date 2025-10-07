package com.broker.walletService.infrastructure.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;   
import com.broker.walletService.domain.Wallet;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    
    Wallet findByOwnerEmail(String ownerEmail);
    
}
