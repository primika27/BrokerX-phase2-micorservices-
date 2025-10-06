package com.broker.walletService.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.broker.walletService.infrastructure.repo.WalletRepository;

@Service
public class WalletService {


    @Autowired
    private WalletRepository walletRepository;


    //  @Transactional
    // public boolean deposerFonds(int clientId, double montant) {
    //     Wallet wallet = walletRepository.findByClient_ClientId(clientId);

    //     if (wallet == null) {
    //          return false;
    //     }

    //     wallet.setBalance(wallet.getBalance() + montant);
    //     walletRepository.save(wallet);
        
            
    //         System.out.println("Dépôt créé: " + montant + "€ + Transaction ID: " + transaction.getTransactionId());
    //         return true;
    //     }
    

    
}
