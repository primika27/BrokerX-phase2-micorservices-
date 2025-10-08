package com.broker.walletService.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.broker.walletService.infrastructure.repo.WalletRepository;
import com.broker.walletService.domain.Wallet;

@Service
public class WalletService {

    @Autowired
    private WalletRepository walletRepository;

    public Wallet createWallet(String ownerEmail) {
        Wallet existingWallet = walletRepository.findByOwnerEmail(ownerEmail);
        if (existingWallet != null) {
            return existingWallet; // Wallet already exists
        }
        
        Wallet newWallet = new Wallet(ownerEmail);
        return walletRepository.save(newWallet);
    }

    public Wallet getWalletByEmail(String ownerEmail) {
        return walletRepository.findByOwnerEmail(ownerEmail);
    }

    @Transactional
    public boolean deposit(String ownerEmail, Double amount) {
        if (amount <= 0) {
            return false;
        }

        Wallet wallet = walletRepository.findByOwnerEmail(ownerEmail);
        if (wallet == null) {
            // Create wallet if it doesn't exist
            wallet = createWallet(ownerEmail);
        }

        wallet.setBalance(wallet.getBalance() + amount);
        walletRepository.save(wallet);

        System.out.println("Dépôt de " + amount + "$ effectué pour " + ownerEmail);
        return true;
    }

    public Double getBalance(String ownerEmail) {
        Wallet wallet = walletRepository.findByOwnerEmail(ownerEmail);
        return wallet != null ? wallet.getBalance() : 0.0;
    }

    @Transactional
    public boolean debit(String ownerEmail, double amount) {
        if (amount <= 0) {
            return false;
        }

        Wallet wallet = walletRepository.findByOwnerEmail(ownerEmail);
        if (wallet == null) {
            System.out.println("Wallet not found for " + ownerEmail);
            return false; // Wallet doesn't exist
        }

        if (wallet.getBalance() < amount) {
            System.out.println("Insufficient funds for " + ownerEmail + ". Balance: " + wallet.getBalance() + ", Required: " + amount);
            return false; // Insufficient funds
        }

        wallet.setBalance(wallet.getBalance() - amount);
        walletRepository.save(wallet);
        
        System.out.println("Débit de " + amount + "$ effectué pour " + ownerEmail + ". Nouveau solde: " + wallet.getBalance());
        return true;
    }
}
