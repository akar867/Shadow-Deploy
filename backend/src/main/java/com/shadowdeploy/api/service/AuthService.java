package com.shadowdeploy.api.service;

import com.shadowdeploy.api.dto.AuthResponse;
import com.shadowdeploy.api.dto.LoginRequest;
import com.shadowdeploy.api.entity.AuthToken;
import com.shadowdeploy.api.entity.UserAccount;
import com.shadowdeploy.api.repository.AuthTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final AuthTokenRepository authTokenRepository;
    private final UserService userService;

    public AuthService(AuthTokenRepository authTokenRepository, UserService userService) {
        this.authTokenRepository = authTokenRepository;
        this.userService = userService;
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = userService.validateCredentials(request.username(), request.password());
        authTokenRepository.deleteByExpiresAtBefore(Instant.now());
        AuthToken token = new AuthToken();
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setUser(user);
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(TOKEN_TTL));
        AuthToken saved = authTokenRepository.save(token);
        return new AuthResponse(saved.getToken(), userService.toProfile(user), saved.getExpiresAt().toString());
    }

    public Optional<UserAccount> findUserByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        authTokenRepository.deleteByExpiresAtBefore(Instant.now());
        return authTokenRepository.findByToken(rawToken)
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .map(AuthToken::getUser);
    }

    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing token");
        }
        authTokenRepository.findByToken(rawToken).ifPresent(authTokenRepository::delete);
    }
}
