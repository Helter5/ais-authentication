package sk.gkanocz.aisauth.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.auth.AdminSessionRepository;
import sk.gkanocz.aisauth.verification.VerificationCodeRepository;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredDataCleanupJob {

    private final VerificationCodeRepository verificationCodeRepository;
    private final AdminSessionRepository adminSessionRepository;

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        verificationCodeRepository.deleteByExpiresAtBefore(now);
        adminSessionRepository.deleteByExpiresAtBefore(now);
        log.debug("Cleaned up expired verification codes and admin sessions");
    }
}
