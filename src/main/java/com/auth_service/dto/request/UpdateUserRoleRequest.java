package com.auth_service.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateUserRoleRequest(
    @NotEmpty(message = "Cần ít nhất một vai trò")
    Set<String> roles
) {}
