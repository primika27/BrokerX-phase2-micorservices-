package com.broker.clientService.Application;
import com.broker.clientService.Infrastructure.client.AuthClient;
import com.broker.clientService.domain.Client;
import com.broker.clientService.Infrastructure.Repo.ClientRepository;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.broker.clientService.Infrastructure.client.UserCredentialRequest;
import java.util.UUID;

@Service
public class ClientService {
    

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private AuthClient authClient;


public Client register(String name, String email, String motDePasse) {
    if (clientRepository.findByEmail(email) != null) {
        throw new IllegalArgumentException("Email already used");
    }

    // 1. Tell Auth Service to register credentials
    authClient.createUserCredential(new UserCredentialRequest(email, motDePasse));

    // 2. Create the client profile
    int verificationToken = UUID.randomUUID().hashCode();
    Client client = new Client(0, name, email, verificationToken, "PENDING");
    client = clientRepository.save(client);
    sendVerificationEmail(client);
    return client;
    }

    public void sendVerificationEmail(Client client) {
        String to = client.getEmail();
        String subject  = "Verification de votre compte";
        String verificationUrl = "http://localhost:8080/api/clients/verify?token=" + client.getVerificationToken();
        // Utiliser des guillemets doubles pour href et pas d'accents dans l'attribut
        String html = "<p>Cliquez ici pour verifier votre compte : <a href=\"" + verificationUrl + "\">Verifier mon compte</a></p>";
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("noreply@brokerx.com");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true); // true = HTML
            mailSender.send(message);
            System.out.println("Verification email sent to: " + to);
        } catch (Exception e) {
            System.out.println("Failed to send verification email to: " + to + " - " + e.getMessage());
        }
    }


    public boolean verifyByToken(int token) {
        Client client = clientRepository.findAll()
            .stream()
            .filter(c -> c.getVerificationToken() == token)
            .findFirst()
            .orElse(null);
        if (client == null) {
            System.out.println("Verification failed - client not found for token: " + token);
            return false;
        }
        client.setStatut("ACTIVE");

        // TODO: Implement portefeuille functionality
        // if (client.getPortefeuille() == null) {
        //     Portefeuille portefeuille = portefeuilleService.createPortefeuille(client);
        //     client.setPortefeuille(portefeuille);
        // }

        clientRepository.save(client);
        System.out.println("Client " + client.getEmail() + " vérifié avec succès !");
        return true;
    }

    public boolean isDuplicate(String email) {
        return clientRepository.findByEmail(email) != null;
    }

    public Client getClientByEmail(String email) {
        return clientRepository.findByEmail(email);
    }

}
