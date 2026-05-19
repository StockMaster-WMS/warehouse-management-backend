package com.auth_service.security;

import com.auth_service.repository.TokenBlacklistRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                Claims claims = jwtTokenProvider.parseClaims(jwt);
                
                // Kiểm tra loại token phải là ACCESS
                String tokenType = claims.get("token_type", String.class);
                if (!"ACCESS".equals(tokenType)) {
                    log.warn("Attempt to authenticate with non-access token");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Kiểm tra blacklist
                String jti = claims.getId();
                if (tokenBlacklistRepository.existsByTokenJti(jti)) {
                    log.warn("Attempt to authenticate with blacklisted token");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Trích xuất thông tin
                UUID userId = UUID.fromString(claims.getSubject());
                String username = claims.get("username", String.class);
                String rolesString = claims.get("roles", String.class);
                
                Collection<? extends GrantedAuthority> authorities = Collections.emptyList();
                if (StringUtils.hasText(rolesString)) {
                    authorities = Arrays.stream(rolesString.split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            // Sử dụng tên quyền chính xác để khớp với hasAuthority()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        new JwtPrincipal(userId, username, claims.get("email", String.class)), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Could not set user authentication in security context: {}", ex.getMessage());
            // Không set authentication, request sẽ đi tiếp và bị chặn bởi Spring Security nếu API yêu cầu auth
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
