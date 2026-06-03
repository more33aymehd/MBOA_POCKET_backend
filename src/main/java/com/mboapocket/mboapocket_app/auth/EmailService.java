package com.mboapocket.mboapocket_app.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Mboapocket — Code de vérification");
        message.setText(
            "Bonjour,\n\n" +
            "Votre code de vérification Mboapocket est :\n\n" +
            "   " + code + "\n\n" +
            "Ce code expire dans 10 minutes.\n\n" +
            "Si vous n'avez pas demandé ce code, ignorez cet email.\n\n" +
            "— L'équipe Mboapocket"
        );
        mailSender.send(message);
    }
}
