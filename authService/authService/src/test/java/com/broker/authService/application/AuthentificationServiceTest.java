package com.broker.authService.application;

import com.broker.authService.Application.AuthentificationService;
import com.broker.authService.Application.JwtService;
import com.broker.authService.domain.UserCredential;
import com.broker.authService.infrastructure.repo.UserCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthentificationService - Unit Tests")
class AuthentificationServiceTest {

    @Mock
    private UserCredentialRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthentificationService authentificationService;

    private UserCredential testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserCredential();
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedPassword");
        testUser.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("Should register new user successfully")
    void testRegisterNewUser() {
        // Given
        String email = "test@example.com";
        String password = "password123";
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn("hashedPassword");
        when(userRepository.save(any(UserCredential.class))).thenReturn(testUser);

        // When & Then - register method is void
        assertDoesNotThrow(() -> authentificationService.register(email, password));
        verify(userRepository, times(1)).findByEmail(email);
        verify(userRepository, times(1)).save(any(UserCredential.class));
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void testRegisterDuplicateEmail() {
        // Given
        String email = "existing@example.com";
        String password = "password123";
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

        // When & Then
        assertThrows(RuntimeException.class, 
            () -> authentificationService.register(email, password));
        
        verify(userRepository, never()).save(any(UserCredential.class));
    }

    @Test
    @DisplayName("Should login successfully with correct credentials")
    void testLoginWithCorrectCredentials() {
        // Given
        String email = "user@example.com";
        String password = "correctPassword";
        String expectedToken = "jwt-token-123";
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(any(), anyString())).thenReturn(expectedToken);

        // When
        String token = authentificationService.login(email, password);

        // Then
        assertNotNull(token);
        assertEquals(expectedToken, token);
        verify(userRepository, times(1)).findByEmail(email);
        verify(passwordEncoder, times(1)).matches(password, testUser.getPasswordHash());
    }

    @Test
    @DisplayName("Should throw exception with wrong password")
    void testLoginWithIncorrectPassword() {
        // Given
        String email = "user@example.com";
        String wrongPassword = "wrongPassword";
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).thenReturn(false);

        // When & Then
        assertThrows(RuntimeException.class, 
            () -> authentificationService.login(email, wrongPassword));
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void testLoginUserNotFound() {
        // Given
        String email = "nonexistent@example.com";
        String password = "anyPassword";
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, 
            () -> authentificationService.login(email, password));
    }
}
