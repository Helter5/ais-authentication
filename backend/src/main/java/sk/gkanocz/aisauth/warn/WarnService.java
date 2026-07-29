package sk.gkanocz.aisauth.warn;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WarnService {

    private final WarnRepository warnRepository;
    private final WarnThresholdRepository warnThresholdRepository;

    @Transactional
    public Warn addWarn(String guildId, String discordId, String moderatorId, String reason) {
        if (discordId.equals(moderatorId)) {
            throw SelfWarnException.create();
        }
        return warnRepository.save(new Warn(guildId, discordId, moderatorId, reason));
    }

    public long countWarns(String discordId, String guildId) {
        return warnRepository.countByDiscordIdAndGuildId(discordId, guildId);
    }

    public List<Warn> getWarns(String discordId, String guildId) {
        return warnRepository.findByDiscordIdAndGuildIdOrderByCreatedAtDesc(discordId, guildId);
    }

    public List<Warn> getGuildWarns(String guildId) {
        return warnRepository.findByGuildIdOrderByCreatedAtDesc(guildId);
    }

    public List<WarnThreshold> getThresholds(String guildId) {
        return warnThresholdRepository.findByGuildIdOrderByWarnLimitAsc(guildId);
    }

    public Optional<WarnThreshold> matchingThreshold(String guildId, long warnCount) {
        return warnThresholdRepository.findByGuildIdAndWarnLimit(guildId, (int) warnCount)
                .filter(threshold -> !"none".equals(threshold.getAction()));
    }

    /**
     * The highest configured threshold already reached at warnCount, i.e. the punishment tier
     * currently in effect for this user - not just the one exact warn that first crossed it.
     * Used to gate every subsequent warn on the same permission check, not only the triggering one.
     */
    public Optional<WarnThreshold> activeThreshold(String guildId, long warnCount) {
        return warnThresholdRepository.findByGuildIdOrderByWarnLimitAsc(guildId).stream()
                .filter(threshold -> !"none".equals(threshold.getAction()))
                .filter(threshold -> threshold.getWarnLimit() <= warnCount)
                .reduce((first, second) -> second);
    }

    @Transactional
    public Warn removeWarn(Long warnId, String guildId) {
        Warn warn = warnRepository.findByIdAndGuildId(warnId, guildId)
                .orElseThrow(() -> WarnNotFoundException.withId(warnId));
        warnRepository.delete(warn);
        return warn;
    }

    @Transactional
    public long clearWarns(String discordId, String guildId) {
        long count = countWarns(discordId, guildId);
        warnRepository.deleteByDiscordIdAndGuildId(discordId, guildId);
        return count;
    }

    @Transactional
    public void upsertThreshold(String guildId, Integer warnLimit, String action) {
        warnThresholdRepository.findByGuildIdAndWarnLimit(guildId, warnLimit)
                .ifPresentOrElse(
                        threshold -> threshold.setAction(action),
                        () -> warnThresholdRepository.save(new WarnThreshold(guildId, warnLimit, action)));
    }

    @Transactional
    public void removeThreshold(String guildId, Integer warnLimit) {
        warnThresholdRepository.deleteByGuildIdAndWarnLimit(guildId, warnLimit);
    }
}
