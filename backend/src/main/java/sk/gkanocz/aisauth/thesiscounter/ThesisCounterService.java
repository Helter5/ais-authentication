package sk.gkanocz.aisauth.thesiscounter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Owns both persistence and the actual Discord channel-rename side-effect, so the slash command
 * handler and the dashboard REST controller share one code path instead of duplicating the
 * find-or-create-by-channel and rename logic in two places.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThesisCounterService {

    /** Placeholders: {days}, {days_word} (Slovak "den"/"dni"), {label} (lowercased), {target_date} (dd.MM.yyyy).
     *  Kept all-lowercase so the dashboard can auto-lowercase the format field as the user types
     *  (mirroring Discord's own channel-rename input) without corrupting the placeholder syntax. */
    static final String DEFAULT_NAME_FORMAT = "{days}-{days_word}-do-{label}";
    static final String DEFAULT_TODAY_FORMAT = "dnes-{label}";
    private static final DateTimeFormatter TARGET_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ThesisCounterConfigRepository repository;

    public List<ThesisCounterConfig> list(String guildId) {
        return repository.findByGuildIdOrderByCreatedAtAsc(guildId);
    }

    /** Days left until the target date, floored at 0 once it has arrived or passed. */
    public long daysRemaining(ThesisCounterConfig config) {
        return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), config.getTargetDate()));
    }

    /** Shared by {@code /odpocet add} and the dashboard's create. Fails if the room already has a counter. */
    @Transactional
    public ThesisCounterConfig addCounter(
            Guild guild, String channelId, String label, LocalDate targetDate, String nameFormat, String todayFormat) {
        validateLabel(label);
        validateTargetDate(targetDate);
        if (repository.findByGuildIdAndChannelId(guild.getId(), channelId).isPresent()) {
            throw InvalidRequestException.withMessage("This room already has a thesis counter - use edit instead.");
        }
        GuildChannel channel = channel(guild, channelId);

        ThesisCounterConfig config = repository.save(
                new ThesisCounterConfig(guild.getId(), channelId, label, targetDate, channel.getName(), nameFormat, todayFormat));
        applyRename(channel, config);
        return config;
    }

    /** Shared by {@code /odpocet edit} and the dashboard's update. Fails if the room has no counter yet.
     *  {@code newChannelId} may differ from {@code currentChannelId} to move the counter to a
     *  different room - the old room's name is restored and the new room's current name is
     *  captured as the fresh "original" before it gets renamed. */
    @Transactional
    public ThesisCounterConfig editCounter(
            Guild guild, String currentChannelId, String newChannelId, String label, LocalDate targetDate,
            String nameFormat, String todayFormat) {
        validateLabel(label);
        validateTargetDate(targetDate);
        ThesisCounterConfig config = repository.findByGuildIdAndChannelId(guild.getId(), currentChannelId)
                .orElseThrow(ThesisCounterConfigNotFoundException::create);

        if (!newChannelId.equals(currentChannelId)) {
            if (repository.findByGuildIdAndChannelId(guild.getId(), newChannelId).isPresent()) {
                throw InvalidRequestException.withMessage("That room already has a thesis counter.");
            }
            GuildChannel oldChannel = guild.getGuildChannelById(currentChannelId);
            if (oldChannel != null) {
                oldChannel.getManager().setName(config.getOriginalChannelName()).queue(success -> { }, failure -> { });
            }
            GuildChannel newChannel = channel(guild, newChannelId);
            config.moveToChannel(newChannelId, newChannel.getName());
        }

        GuildChannel channel = channel(guild, config.getChannelId());
        config.update(label, targetDate, true, nameFormat, todayFormat);
        applyRename(channel, config);
        return config;
    }

    /** Restores the channel's original name (best-effort) and deletes the row. Shared by
     *  {@code /odpocet remove} and the dashboard delete. */
    @Transactional
    public void removeCounter(Guild guild, ThesisCounterConfig config) {
        GuildChannel channel = guild.getGuildChannelById(config.getChannelId());
        if (channel != null) {
            channel.getManager().setName(config.getOriginalChannelName()).queue(success -> { }, failure -> { });
        }
        repository.deleteByIdAndGuildId(config.getId(), config.getGuildId());
    }

    /** Recomputes and applies today's channel name for one config, deactivating it once the
     *  target date has arrived. Used by the daily scheduled job. */
    @Transactional
    public void applyDailyRename(Guild guild, ThesisCounterConfig config) {
        GuildChannel channel = guild.getGuildChannelById(config.getChannelId());
        if (channel == null) {
            log.warn("Thesis counter {}: channel {} no longer exists, skipping rename", config.getId(), config.getChannelId());
            return;
        }
        applyRename(channel, config);
    }

    private void applyRename(GuildChannel channel, ThesisCounterConfig config) {
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), config.getTargetDate());
        String newName = channelName(daysRemaining, config);
        if (!newName.equals(channel.getName())) {
            channel.getManager().setName(newName).queue();
        }
        if (daysRemaining <= 0 && config.isActive()) {
            config.deactivate();
        }
    }

    static String channelName(long daysRemaining, ThesisCounterConfig config) {
        String template = daysRemaining <= 0
                ? (blankToNull(config.getTodayFormat()) != null ? config.getTodayFormat() : DEFAULT_TODAY_FORMAT)
                : (blankToNull(config.getNameFormat()) != null ? config.getNameFormat() : DEFAULT_NAME_FORMAT);
        return render(template, daysRemaining, config.getLabel(), config.getTargetDate());
    }

    private static String render(String template, long daysRemaining, String label, LocalDate targetDate) {
        String daysWord = daysRemaining == 1 ? "den" : "dni";
        return template
                .replace("{days}", String.valueOf(daysRemaining))
                .replace("{days_word}", daysWord)
                .replace("{label}", label.toLowerCase(Locale.ROOT))
                .replace("{target_date}", targetDate.format(TARGET_DATE_FORMAT));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private GuildChannel channel(Guild guild, String channelId) {
        GuildChannel channel = guild.getGuildChannelById(channelId);
        if (channel == null) {
            throw InvalidRequestException.withMessage("Channel not found.");
        }
        return channel;
    }

    private void validateLabel(String label) {
        if (!"BP".equals(label) && !"DP".equals(label)) {
            throw InvalidRequestException.withMessage("label must be BP or DP.");
        }
    }

    private void validateTargetDate(LocalDate targetDate) {
        if (targetDate.isBefore(LocalDate.now())) {
            throw InvalidRequestException.withMessage("Target date can't be in the past.");
        }
    }
}
