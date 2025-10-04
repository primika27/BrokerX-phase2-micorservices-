package com.broker.clientService.domain;

@Entity
public class Client {


    int clientId;

    public Client() {
    }

    String name;
    String email;
    String motDePasse;
    int verificationToken;
    String statut; //(PENDING, ACTIVE, REJECTED).

    public Client(int clientId, String name, String email, String motDePasse, int verificationToken, String statut) {
        this.clientId = clientId;
        this.name = name;
        this.email = email;
        this.motDePasse = motDePasse;
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
