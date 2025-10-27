package com.broker.authService.infrastructure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.broker.authService.infrastructure.dto.UserCredentialRequest;
import com.broker.authService.infrastructure.dto.AuthResponse;
import com.broker.authService.infrastructure.dto.OtpVerificationRequest;
import com.broker.authService.Application.AuthentificationService;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/auth")
public class AuthentificationController {
    
    private final AuthentificationService authentificationService;

    public AuthentificationController(AuthentificationService authentificationService) {
        this.authentificationService = authentificationService;
    }

    @GetMapping("/test")
    public ResponseEntity<AuthResponse> test() {
        return ResponseEntity.ok(new AuthResponse("OK", null, "AuthService is working!"));
    }

@PostMapping("/simple-login")
public ResponseEntity<AuthResponse> simpleLogin(@RequestBody UserCredentialRequest req) {
  try {
    String token = authentificationService.login(req.getEmail(), req.getPassword());
    return ResponseEntity.ok(new AuthResponse("OK", token, null));
  } catch (Exception e) {
    return ResponseEntity.badRequest().body(new AuthResponse("ERROR", null, e.getMessage()));
  }
}

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody UserCredentialRequest request) {
        try {
            authentificationService.register(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(new AuthResponse("OK", null, "Check your email to verify."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("ERROR", null, e.getMessage()));
        }
    }


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody UserCredentialRequest request) {
        try {
            String result = authentificationService.loginWithMfa(request.getEmail(), request.getPassword());
            
            // Handle different login results with appropriate messages
            String message = null;
            switch (result) {
                case "MFA_REQUIRED":
                    message = "OTP has been sent to your email. Please enter the code.";
                    break;
                case "USER_NOT_FOUND":
                    message = "No account found with this email.";
                    break;
                case "INVALID_PASSWORD":
                    message = "Incorrect password.";
                    break;
                case "ACCOUNT_INACTIVE":
                    message = "Your account is inactive. Please contact support.";
                    break;
            }
            
            return ResponseEntity.ok(new AuthResponse(result, null, message));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("ERROR", null, e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@RequestBody OtpVerificationRequest request) {
        String token = authentificationService.validateOtp(request.getEmail(), request.getOtp());
        if (token != null) {
            return ResponseEntity.ok(new AuthResponse("LOGIN_SUCCESS", token, null));
        } else {
            return ResponseEntity.badRequest().body(new AuthResponse("ERROR", null, "INVALID_OTP"));
        }
    }

    // Logout endpoint
    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(@RequestParam String email) {
        authentificationService.logout(email);
        return ResponseEntity.ok(new AuthResponse("OK", null, "LOGOUT_SUCCESS"));
    }

    // // Email verification endpoint
    // @GetMapping("/verify")
    // public ResponseEntity<AuthResponse> verifyAccount(@RequestParam String email) {
    //     boolean verified = authentificationService.verifyAccount(email);
    //     if (verified) {
    //         return ResponseEntity.ok(new AuthResponse("VERIFICATION_SUCCESS", null, null));
    //     } else {
    //         return ResponseEntity.badRequest().body(new AuthResponse("ERROR", null, "Verification failed. Invalid or already verified account."));
    //     }
    // }

    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam String email) {
    boolean verified = authentificationService.verifyAccount(email);
    if (verified) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header("Location", "http://localhost:5173/verify-success")
            .build();
    } else {
        //Redirect to failure page or show generic error
        return ResponseEntity.status(HttpStatus.FOUND)
            .header("Location", "http://localhost:5173/verify-failed")
            .build();
    }
}

}
