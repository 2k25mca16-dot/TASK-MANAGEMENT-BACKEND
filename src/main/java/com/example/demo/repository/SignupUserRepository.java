package com.example.demo.repository;

import com.example.demo.model.SignupUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SignupUserRepository extends JpaRepository<SignupUser, Long> {
    Optional<SignupUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    void deleteByEmailIgnoreCase(String email);
}

