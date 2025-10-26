package com.broker.authService.infrastructure.dto;
//com.broker.authService.infrastructure.dto.AuthResponse

public class AuthResponse {
  private String status;   // e.g. OK, MFA_REQUIRED, INVALID_CREDENTIALS
  private String token;    // optional
  private String message;  // optional
  public AuthResponse() {}
  public AuthResponse(String status, String token, String message) {
    this.status = status; this.token = token; this.message = message;
  }
  public String getStatus() { return status; }
  public void setStatus(String s) { this.status = s; }
  public String getToken() { return token; }
  public void setToken(String t) { this.token = t; }
  public String getMessage() { return message; }
  public void setMessage(String m) { this.message = m; }
}

