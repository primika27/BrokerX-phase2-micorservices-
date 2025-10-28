package com.broker.walletService.Application;

import com.broker.walletService.domain.Wallet;
import com.broker.walletService.infrastructure.repo.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService - Unit Tests")
class WalletServiceUnitTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletService walletService;

    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        testWallet = new Wallet("test@example.com");
        testWallet.setBalance(1000.0);
    }

    @Test
    @DisplayName("Should create new wallet successfully")
    void testCreateWallet() {
        // Given
        when(walletRepository.findByOwnerEmail(anyString())).thenReturn(null);
        when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);

        // When
        Wallet result = walletService.createWallet("test@example.com");

        // Then
        assertNotNull(result);
        assertEquals("test@example.com", result.getOwnerEmail());
        verify(walletRepository, times(1)).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should return existing wallet if already exists")
    void testCreateWalletAlreadyExists() {
        // Given
        when(walletRepository.findByOwnerEmail(anyString())).thenReturn(testWallet);

        // When
        Wallet result = walletService.createWallet("test@example.com");

        // Then
        assertNotNull(result);
        assertEquals("test@example.com", result.getOwnerEmail());
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should deposit money successfully")
    void testDeposit() {
        // Given
        when(walletRepository.findByOwnerEmail(anyString())).thenReturn(testWallet);
        when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);

        // When
        boolean result = walletService.deposit("test@example.com", 500.0);

        // Then
        assertTrue(result);
        assertEquals(1500.0, testWallet.getBalance());
        verify(walletRepository, times(1)).save(testWallet);
    }

    @Test
    @DisplayName("Should reject negative deposit amount")
    void testDepositNegativeAmount() {
        // When
        boolean result = walletService.deposit("test@example.com", -100.0);

        // Then
        assertFalse(result);
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should get balance successfully")
    void testGetBalance() {
        // Given
        when(walletRepository.findByOwnerEmail(anyString())).thenReturn(testWallet);

        // When
        Double balance = walletService.getBalance("test@example.com");

        // Then
        assertNotNull(balance);
        assertEquals(1000.0, balance);
        verify(walletRepository, times(1)).findByOwnerEmail("test@example.com");
    }

    @Test
    @DisplayName("Should return 0 when wallet not found")
    void testGetBalanceWalletNotFound() {
        // Given
        when(walletRepository.findByOwnerEmail(anyString())).thenReturn(null);

        // When
        Double balance = walletService.getBalance("nonexistent@example.com");

        // Then
        assertEquals(0.0, balance);
    }

    @Test
    @DisplayName("Should debit successfully with sufficient funds")
    void testDebitSuccess() {
        // Given
        when(walletRepository.findByOwnerEmail(anyString())).thenReturn(testWallet);
        when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);

        // When
        boolean result = walletService.debit("test@example.com", 300.0);

        // Then
        assertTrue(result);
        assertEquals(700.0, testWallet.getBalance());
        verify(walletRepository, times(1)).save(testWallet);
    }

    @Test
    @DisplayName("Should reject debit with insufficient funds")
    void testDebitInsufficientFunds() {
        // Given
        when(walletRepository.findByOwnerEmail(anyString())).thenReturn(testWallet);

        // When
        boolean result = walletService.debit("test@example.com", 2000.0);

        // Then
        assertFalse(result);
        assertEquals(1000.0, testWallet.getBalance()); // Balance unchanged
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should reject debit when wallet not found")
    void testDebitWalletNotFound() {
        // Given
        when(walletRepository.findByOwnerEmail(anyString())).thenReturn(null);

        // When
        boolean result = walletService.debit("nonexistent@example.com", 100.0);

        // Then
        assertFalse(result);
        verify(walletRepository, never()).save(any(Wallet.class));
    }
}
