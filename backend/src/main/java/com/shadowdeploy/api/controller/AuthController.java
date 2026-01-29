package com.shadowdeploy.api.controller;

import com.shadowdeploy.api.dto.AuthResponse;
import com.shadowdeploy.api.dto.LoginRequest;
import com.shadowdeploy.api.dto.UserProfileResponse;
import com.shadowdeploy.api.entity.UserAccount;
import com.shadowdeploy.api.service.AuthService;
import com.shadowdeploy.api.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserProfileResponse me(@RequestAttribute("shadowUser") UserAccount user) {
        return userService.toProfile(user);
    }

    @PostMapping("/logout")
    public void logout(@RequestAttribute("shadowToken") String token) {
        authService.logout(token);
    }
}
