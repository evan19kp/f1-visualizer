package com.evanp.f1.api.auth;

public record LoginResponse(String token, long expiresInMs) {}
