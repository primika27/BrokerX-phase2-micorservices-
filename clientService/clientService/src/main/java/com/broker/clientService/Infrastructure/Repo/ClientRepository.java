package com.broker.clientService.Infrastructure.Repo;

import com.broker.clientService.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Integer> {
    Client findByEmail(String email);
}
