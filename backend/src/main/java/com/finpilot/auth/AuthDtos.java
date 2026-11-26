package com.finpilot.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.finpilot.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain letters and digits")
            String password,
            @NotBlank @Size(max = 100) String displayName) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password) {}

    public record MfaLoginRequest(@NotBlank String mfaToken, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    public record CodeRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record UserDto(long id, String email, String displayName, String role, boolean mfaEnabled,
                          String currency) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), u.isMfaEnabled(),
                    "INR");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(boolean mfaRequired, String mfaToken, String accessToken, String refreshToken,
                                Long expiresIn, UserDto user) {
        public static LoginResponse mfa(String mfaToken) {
            return new LoginResponse(true, mfaToken, null, null, null, null);
        }

        public static LoginResponse tokens(String access, String refresh, long expiresIn, UserDto user) {
            return new LoginResponse(false, null, access, refresh, expiresIn, user);
        }
    }

    public record MfaSetupResponse(String secret, String otpauthUri) {}
}
