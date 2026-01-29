package com.shadowdeploy.api.service;

import com.shadowdeploy.api.dto.UserProfileResponse;
import com.shadowdeploy.api.entity.UserAccount;
import com.shadowdeploy.api.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class UserService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount ensureDefaultUser() {
        Optional<UserAccount> existing = userRepository.findByUsername("admin");
        if (existing.isPresent()) {
            return existing.get();
        }
        UserAccount user = new UserAccount();
        user.setUsername("admin");
        user.setDisplayName("Shadow Admin");
        user.setRole("admin");
        user.setPasswordHash(passwordEncoder.encode("shadowdeploy"));
        return userRepository.save(user);
    }

    public UserAccount validateCredentials(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required");
        }
        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return user;
    }

    public UserProfileResponse toProfile(UserAccount user) {
        return new UserProfileResponse(user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
