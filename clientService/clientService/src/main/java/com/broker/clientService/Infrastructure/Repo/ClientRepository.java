package com.brokerx.clientservice.infrastructure.repository;

import com.broker.clientservice.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Integer> {
    Client findByEmail(String email);
}
