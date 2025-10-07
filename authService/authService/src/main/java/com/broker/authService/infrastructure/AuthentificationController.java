package com.broker.authService.infrastructure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.broker.authService.infrastructure.dto.UserCredentialRequest;
import com.broker.authService.Application.AuthentificationService;

@RestController
@RequestMapping("/api/auth")
public class AuthentificationController {
    
    private final AuthentificationService authentificationService;

    public AuthentificationController(AuthentificationService authentificationService) {
        this.authentificationService = authentificationService;
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("AuthService is working!");
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserCredentialRequest request) {
        authentificationService.register(request.getEmail(), request.getPassword());
        return ResponseEntity.ok("User credentials created successfully. Please check your email to verify your account.");
    }


    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String motDePasse) {
        return authentificationService.loginWithMfa(email, motDePasse);
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam String email, @RequestParam String otp) {
        return authentificationService.validateOtp(email, otp) ? "LOGIN_SUCCESS" : "INVALID_OTP";
    }

    // Logout endpoint
    @PostMapping("/logout")
    public String logout(@RequestParam String email) {
        authentificationService.logout(email);
        return "LOGOUT_SUCCESS";
    }

    // Email verification endpoint
    @GetMapping("/verify")
    public ResponseEntity<String> verifyAccount(@RequestParam String email) {
        boolean verified = authentificationService.verifyAccount(email);
        if (verified) {
            return ResponseEntity.ok("Account verified successfully! You can now login.");
        } else {
            return ResponseEntity.badRequest().body("Verification failed. Invalid or already verified account.");
        }
    }

}
