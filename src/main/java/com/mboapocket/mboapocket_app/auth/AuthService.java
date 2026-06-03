package com.mboapocket.mboapocket_app.auth;

import com.mboapocket.mboapocket_app.auth.dto.AuthResponse;
import com.mboapocket.mboapocket_app.auth.dto.LoginRequest;
import com.mboapocket.mboapocket_app.auth.dto.RegisterRequest;
import com.mboapocket.mboapocket_app.auth.dto.VerifyTwoFactorRequest;
import com.mboapocket.mboapocket_app.user.User;
import com.mboapocket.mboapocket_app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final TwoFactorStore twoFactorStore;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()))
            throw new IllegalArgumentException("Email déjà utilisé");

        User user = User.builder()
                .nom(request.getNom())
                .email(request.getEmail())
                .telephone(request.getTelephone())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved.getId(), saved.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(saved.getId())
                .nom(saved.getNom())
                .email(saved.getEmail())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email ou mot de passe incorrect"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
            throw new IllegalArgumentException("Email ou mot de passe incorrect");

        String sessionData = twoFactorStore.createSession(user.getEmail());
        String[] parts = sessionData.split("\\|");
        String tempToken = parts[0];
        String code = parts[1];

        emailService.sendVerificationCode(user.getEmail(), code);

        return AuthResponse.builder()
                .requiresTwoFactor(true)
                .tempToken(tempToken)
                .build();
    }

    public AuthResponse verifyTwoFactor(VerifyTwoFactorRequest request) {
        String email = twoFactorStore.verify(request.getTempToken(), request.getCode());
        if (email == null)
            throw new IllegalArgumentException("Code invalide ou expiré.");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .nom(user.getNom())
                .email(user.getEmail())
                .build();
    }
}
