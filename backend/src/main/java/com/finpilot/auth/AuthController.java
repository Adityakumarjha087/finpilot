package com.finpilot.auth;

import com.finpilot.auth.AuthDtos.CodeRequest;
import com.finpilot.auth.AuthDtos.LoginRequest;
import com.finpilot.auth.AuthDtos.LoginResponse;
import com.finpilot.auth.AuthDtos.MfaLoginRequest;
import com.finpilot.auth.AuthDtos.MfaSetupResponse;
import com.finpilot.auth.AuthDtos.RefreshRequest;
import com.finpilot.auth.AuthDtos.RegisterRequest;
import com.finpilot.auth.AuthDtos.UserDto;
import com.finpilot.common.ClientIp;
import com.finpilot.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    @Operation(summary = "Password login; returns tokens or an MFA challenge")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, ClientIp.of(http));
    }

    @PostMapping("/login/mfa")
    public LoginResponse loginMfa(@Valid @RequestBody MfaLoginRequest req, HttpServletRequest http) {
        return auth.loginMfa(req, ClientIp.of(http));
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
    }

    @GetMapping("/me")
    public UserDto me() {
        return auth.me(CurrentUser.id());
    }

    @PostMapping("/mfa/setup")
    public MfaSetupResponse setupMfa() {
        return auth.setupMfa(CurrentUser.id());
    }

    @PostMapping("/mfa/enable")
    public UserDto enableMfa(@Valid @RequestBody CodeRequest req) {
        return auth.enableMfa(CurrentUser.id(), req.code());
    }

    @PostMapping("/mfa/disable")
    public UserDto disableMfa(@Valid @RequestBody CodeRequest req) {
        return auth.disableMfa(CurrentUser.id(), req.code());
    }
}
