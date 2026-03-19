package com.auth_service.service;

import com.auth_service.repository.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistCleanupJob {

    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Scheduled(fixedDelayString = "${auth.blacklist.cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanupExpiredBlacklistedTokens() {
        int deleted = tokenBlacklistRepository.deleteExpiredTokens(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned {} expired blacklisted tokens", deleted);
        }
    }
}
