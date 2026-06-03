package com.mboapocket.mboapocket_app.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long userId;
    private String nom;
    private String email;
    // 2FA
    private boolean requiresTwoFactor;
    private String tempToken;
}
