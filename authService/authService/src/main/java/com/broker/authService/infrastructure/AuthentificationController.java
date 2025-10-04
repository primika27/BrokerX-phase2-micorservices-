package com.broker.authService.infrastructure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.broker.authService.Application.AuthentificationService;

public class AuthentificationController {
    
    private final AuthentificationService authentificationService;

    public AuthentificationController(AuthentificationService authentificationService) {
        this.authentificationService = authentificationService;
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


}
