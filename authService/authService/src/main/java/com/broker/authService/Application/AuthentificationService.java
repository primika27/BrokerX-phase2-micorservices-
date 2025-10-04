package com.broker.authService.Application;
import jakarta.mail.internet.MimeMessage;

import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.broker.authService.domain.UserCredential;


public class AuthentificationService {


 private final Random random = new Random();

    private final java.util.Map<Integer, String> otpStore = new java.util.HashMap<>();


    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserCredentialRepository repository;

    @PostMapping("/register-credentials")
    public void registerCredentials(@RequestParam String email, @RequestParam String password) {
        authService.register(email, password); // This hashes and saves password
    }


    public String loginWithMfa(String identifiant, String motDePasse) {
        Optional<UserCredential> userOpt = repository.findByEmail(identifiant);
        if (userOpt.isEmpty()) {
            return "USER_NOT_FOUND";
        }

        UserCredential user = userOpt.get();

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return "ACCOUNT_INACTIVE";
        }

        if (!user.getPasswordHash().equals(password)) { //later replace with passwordEncoder.matches
            return "INVALID_PASSWORD";
        }

        // MFA
        String otp = String.format("%06d", random.nextInt(1_000_000));
        otpStore.put(user.getId(), otp);
        sendOtp(user.getEmail(), otp);
        return "MFA_REQUIRED";
    }


    private void sendOtp(String to, String otp) {
        try {
            String subject = "BrokerX – Votre code de vérification";
            String html = "<p>Votre code OTP est : <strong>" + otp + "</strong></p>";

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("noreply@brokerx.com");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);

            System.out.println("OTP sent to: " + to);
        } catch (Exception e) {
            System.out.println("Failed to send OTP: " + e.getMessage());
        }
    }

    public void logout(String email) {
        Client client = clientRepository.findByEmail(email);
        if (client != null) {
            otpStore.remove(client.getClientId());
        }
    }
    public void logout(int clientId) {
    repository.findByEmail(email).ifPresent(u -> otpStore.remove(u.getId()));
    }

    public boolean validateOtp(String email, String otp) {
        Optional<UserCredential> userOpt = repository.findByEmail(email);
        if (userOpt.isEmpty()) return false;

        UserCredential user = userOpt.get();
        String expectedOtp = otpStore.get(user.getId());
        boolean valid = otp != null && otp.equals(expectedOtp);

        if (valid) otpStore.remove(user.getId());
        return valid;
    }


    
}
