package sk.gkanocz.aisauth.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.thesiscounter.ThesisCounterConfig;
import sk.gkanocz.aisauth.thesiscounter.ThesisCounterConfigRepository;
import sk.gkanocz.aisauth.thesiscounter.ThesisCounterService;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThesisCounterRenameJob {

    private final ThesisCounterConfigRepository thesisCounterConfigRepository;
    private final ThesisCounterService thesisCounterService;
    private final DiscordBotService discordBotService;

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void renameActiveCounters() {
        Optional<JDA> jda = discordBotService.jda();
        if (jda.isEmpty()) {
            return;
        }

        for (ThesisCounterConfig config : thesisCounterConfigRepository.findByActiveTrue()) {
            try {
                Guild guild = jda.get().getGuildById(config.getGuildId());
                if (guild == null) {
                    log.warn("Thesis counter {}: guild {} not available, skipping", config.getId(), config.getGuildId());
                    continue;
                }
                thesisCounterService.applyDailyRename(guild, config);
            } catch (Exception e) {
                log.error("Thesis counter {}: rename failed", config.getId(), e);
            }
        }
    }
}
