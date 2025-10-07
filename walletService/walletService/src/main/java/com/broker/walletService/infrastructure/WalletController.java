
package com.broker.walletService.infrastructure;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import com.broker.walletService.infrastructure.repo.*;
import com.broker.walletService.Application.WalletService;
import com.broker.walletService.domain.Wallet;
import org.springframework.beans.factory.annotation.Autowired;

@Controller
public class WalletController {

    @Autowired
    private final WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }


    // @GetMapping("/deposit")
    // public String showDepositPage(Model model, Principal principal) {
    //     try {
    //         if (client != null) {
    //             Portefeuille portefeuille = portefeuilleService.getPortefeuilleByClientId(client.getClientId());
    //             if (portefeuille != null) {
    //                 model.addAttribute("currentBalance", portefeuille.getSolde());
    //             } else {
    //                 model.addAttribute("currentBalance", 0.0);
    //             }
    //         }
    //         model.addAttribute("maxDeposit", MAX_DEPOSIT_LIMIT);
    //     } catch (Exception e) {
    //         model.addAttribute("currentBalance", 0.0);
    //         model.addAttribute("maxDeposit", MAX_DEPOSIT_LIMIT);
    //     }
    //     return "deposit";
    // }

    @PostMapping("/api/wallet/deposit")
    @ResponseBody
    public String deposit(@RequestParam double amount) {
        if (amount <= 0) {
            return "Deposit amount must be positive.";
        }
        try {
            // Assuming you have a way to get the authenticated user's email
            String ownerEmail = walletRepository.findByOwnerEmail();
            boolean success = walletService.deposit(ownerEmail, amount);
            if (success) {
                return "Deposit of " + amount + "$ successful.";
            } else {
                return "Deposit failed. Please try again.";
            }
        } catch (Exception e) {
            return "An error occurred: " + e.getMessage();
        }
    }

    @GetMapping("/api/wallet/balance")
    @ResponseBody
    public Double getBalance(Principal principal) {
        try {
            Wallet wallet = walletService.getWalletByEmail(principal.getName());
            if (wallet != null) {
                return wallet.getBalance();
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

}
