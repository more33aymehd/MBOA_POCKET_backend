package com.mboapocket.mboapocket_app.auth.dto;

import lombok.Data;

@Data
public class VerifyTwoFactorRequest {
    private String tempToken;
    private String code;
}
