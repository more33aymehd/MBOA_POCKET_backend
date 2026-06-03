package com.mboapocket.mboapocket_app.auth;

import com.mboapocket.mboapocket_app.auth.dto.AuthResponse;
import com.mboapocket.mboapocket_app.auth.dto.LoginRequest;
import com.mboapocket.mboapocket_app.auth.dto.RegisterRequest;
import com.mboapocket.mboapocket_app.auth.dto.VerifyTwoFactorRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-2fa")
    ResponseEntity<AuthResponse> verifyTwoFactor(@RequestBody VerifyTwoFactorRequest request) {
        return ResponseEntity.ok(authService.verifyTwoFactor(request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
