package com.auth_service.security;

import java.security.Principal;
import java.util.UUID;

public record JwtPrincipal(
        UUID userId,
        String username,
        String email
) implements Principal {

    @Override
    public String getName() {
        return userId == null ? "" : userId.toString();
    }
}
