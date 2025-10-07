package com.broker.authService.infrastructure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.broker.authService.infrastructure.dto.UserCredentialRequest;
import com.broker.authService.infrastructure.dto.OtpVerificationRequest;
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

    @PostMapping("/simple-login") // pr test without mfa
    public ResponseEntity<String> simpleLogin(@RequestBody UserCredentialRequest request) {
        try {
            String result = authentificationService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok("Login successful. Token: " + result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserCredentialRequest request) {
        authentificationService.register(request.getEmail(), request.getPassword());
        return ResponseEntity.ok("User credentials created successfully. Please check your email to verify your account.");
    }


    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody UserCredentialRequest request) {
        try {
            String result = authentificationService.loginWithMfa(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody OtpVerificationRequest request) {
        String token = authentificationService.validateOtp(request.getEmail(), request.getOtp());
        if (token != null) {
            return ResponseEntity.ok("LOGIN_SUCCESS. Token: " + token);
        } else {
            return ResponseEntity.badRequest().body("INVALID_OTP");
        }
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
