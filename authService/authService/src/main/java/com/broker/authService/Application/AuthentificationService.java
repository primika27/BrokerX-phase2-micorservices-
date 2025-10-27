package com.broker.authService.Application;
import jakarta.mail.internet.MimeMessage;

import java.util.Optional;
import java.util.Random;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.broker.authService.infrastructure.repo.UserCredentialRepository;
import com.broker.authService.domain.UserCredential;
import com.broker.authService.Application.JwtService;

@Service
public class AuthentificationService {


    private final Random random = new Random();

    private final java.util.Map<Long, String> otpStore = new java.util.HashMap<>();


    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserCredentialRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    public String login(String email, String password) {
    UserCredential user = repository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
        throw new RuntimeException("Invalid credentials");
    }
    

    // Generate JWT token
    return jwtService.generateToken(Map.of("role", "CLIENT"), user.getEmail());
    }

    public void register(String email, String rawPassword) {
        if (repository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        String hashedPassword = passwordEncoder.encode(rawPassword);
        UserCredential user = new UserCredential(email, hashedPassword, "PENDING");
        repository.save(user);
        
        // Send verification email
        sendVerificationEmail(user);
    }



    public String loginWithMfa(String identifiant, String motDePasse) {
        Optional<UserCredential> userOpt = repository.findByEmail(identifiant);
        if (userOpt.isEmpty()) {
            return "USER_NOT_FOUND";
        }

        UserCredential user = userOpt.get();

        if (!passwordEncoder.matches(motDePasse, user.getPasswordHash())) {
            return "INVALID_PASSWORD";
        }

        // Check if account is completely inactive/blocked
        if (!"PENDING".equalsIgnoreCase(user.getStatus()) && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return "ACCOUNT_INACTIVE";
        }

        // For all valid users (PENDING or ACTIVE), send OTP for login authentication
        String otp = String.format("%06d", random.nextInt(1_000_000));
        otpStore.put(user.getId(), otp);
        System.out.println("DEBUG - otp: " + otp);
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

    private void sendVerificationEmail(UserCredential user) {
        try {
            String subject = "BrokerX – Vérification de votre compte";
            String verificationUrl = "http://localhost:8080/api/auth/verify?email=" + user.getEmail();
            String html = "<p>Cliquez ici pour vérifier votre compte : <a href=\"" + verificationUrl + "\">Vérifier mon compte</a></p>";

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("noreply@brokerx.com");
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);

            System.out.println("Verification email sent to: " + user.getEmail());
        } catch (Exception e) {
            System.out.println("Failed to send verification email to: " + user.getEmail() + " - " + e.getMessage());
        }
    }

    public void logout(String email) {
        repository.findByEmail(email).ifPresent(u -> otpStore.remove(u.getId()));
    }

    public String validateOtp(String email, String otp) {
        Optional<UserCredential> userOpt = repository.findByEmail(email);
        if (userOpt.isEmpty()) return null;

        UserCredential user = userOpt.get();
        String expectedOtp = otpStore.get(user.getId());
        boolean valid = otp != null && otp.equals(expectedOtp);

        if (valid) {
            otpStore.remove(user.getId());
            // Generate JWT token after successful OTP validation
            return jwtService.generateToken(Map.of("role", "CLIENT"), user.getEmail());
        }
        return null;
    }

    public boolean verifyAccount(String email) {
        Optional<UserCredential> userOpt = repository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return false;
        }

        UserCredential user = userOpt.get();
        if (!"PENDING".equals(user.getStatus())) {
            return false; // Already verified or rejected
        }

        user.setStatus("ACTIVE");
        repository.save(user);
        System.out.println("Account verified for: " + email);
        return true;
    }

}
