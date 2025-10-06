
package com.broker.walletService.infrastructure;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.broker.walletService.Application.WalletService;
import org.springframework.beans.factory.annotation.Autowired;

@Controller
public class WalletController {

    @Autowired
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }


    //     @GetMapping("/deposit")
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

    
    // @PostMapping("/api/portefeuille/deposit")  
    // @ResponseBody
    // public String deposit(@RequestParam double amount, Principal principal) {
    //     try {
    //         if (amount <= 0) {
    //             return "ERROR: Le montant doit être positif";
    //         }

    //         if (amount > MAX_DEPOSIT_LIMIT) {
    //             return "ERROR: Dépôt refusé - Limite anti-fraude dépassée (max: " + 
    //                    String.format("%.2f", MAX_DEPOSIT_LIMIT) + "€)";
    //         }

    //         Client client = clientService.findByEmail(principal.getName());
    //         if (client == null) {
    //             return "ERROR: Client non trouvé";
    //         }

    //         Portefeuille portefeuille = portefeuilleService.getPortefeuilleByClientId(client.getClientId());
    //         if (portefeuille != null && (portefeuille.getSolde() + amount) > MAX_DEPOSIT_LIMIT) {
    //             return "ERROR: Dépôt refusé - Le solde total dépasserait la limite de " + 
    //                    String.format("%.2f", MAX_DEPOSIT_LIMIT) + "€";
    //         }

    //         boolean success = portefeuilleService.deposerFonds(client.getClientId(), amount);
            
    //         if (success) {
    //             // Réponse texte simple que le front sait déjà gérer
    //             return "SUCCESS: Dépôt de " + String.format("%.2f", amount) + "€ effectué avec succès";
    //         } else {
    //             return "ERROR: Échec du dépôt";
    //         }
            
    //     } catch (Exception e) {
    //         return "ERROR: " + e.getMessage();
    //     }
    // }

    // @GetMapping("/api/portefeuille/balance")
    // @ResponseBody
    // public Double getBalance(Principal principal) {
    //     try {
    //         Client client = clientService.findByEmail(principal.getName());
    //         if (client == null) {
    //             return 0.0;
    //         }
    //         Portefeuille portefeuille = portefeuilleService.getPortefeuilleByClientId(client.getClientId());
    //         if (portefeuille != null) {
    //             return portefeuille.getSolde();
    //         }
    //         return 0.0;
    //     } catch (Exception e) {
    //         return 0.0;
    //     }
    // }




}
