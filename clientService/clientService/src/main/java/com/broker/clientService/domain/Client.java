package com.broker.clientService.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int clientId;


    private String name;
    private String email;

    private int verificationToken;
    private String statut; //(PENDING, ACTIVE, REJECTED).

    public Client() {
    }
    public Client(int clientId, String name, String email, int verificationToken, String statut) {
        this.clientId = clientId;
        this.name = name;
        this.email = email;
        this.verificationToken = verificationToken;
        this.statut = statut;
    }
    
    // Getters and setters
    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getVerificationToken() {
        return verificationToken;
    }
    public void setVerificationToken(int verificationToken) {
        this.verificationToken = verificationToken;
    }
    public String getStatut() {
        return statut;
    }
    public void setStatut(String statut) {
        this.statut = statut;
    }
}
