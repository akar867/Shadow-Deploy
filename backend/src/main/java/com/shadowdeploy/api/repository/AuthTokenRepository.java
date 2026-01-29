package com.shadowdeploy.api.repository;

import com.shadowdeploy.api.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByToken(String token);

    long deleteByExpiresAtBefore(Instant time);
}
