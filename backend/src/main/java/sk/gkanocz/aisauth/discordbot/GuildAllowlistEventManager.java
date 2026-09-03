package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.IEventManager;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import sk.gkanocz.aisauth.settings.AdminSettingsService;

import java.util.List;

/**
 * Wraps JDA's default event dispatch so every guild-scoped event (messages, button clicks,
 * select-menu choices) is dropped in one place for guilds that aren't on the "allowed_guild_ids"
 * allowlist, instead of every automod/rolemenu listener repeating the same check. Slash
 * commands are deliberately exempt here — CommandInteractionListener does its own allowlist check
 * so it can reply explaining why the command was blocked, which a silent drop at this layer can't do.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuildAllowlistEventManager implements IEventManager {

    private final AdminSettingsService adminSettingsService;
    private final IEventManager delegate = new InterfacedEventManager();

    @Override
    public void handle(GenericEvent event) {
        String guildId = guildIdOf(event);
        if (guildId != null && !adminSettingsService.isGuildAllowed(guildId)) {
            return;
        }
        try {
            delegate.handle(event);
        } catch (TransactionException | DataAccessException e) {
            // Single chokepoint for every listener. During a Postgres outage the best-effort ones
            // (automod on each message, autocomplete, ...) would otherwise each throw an uncaught
            // exception JDA logs with a full stack trace - once per message. The user-facing
            // interaction handlers do their own graceful handling before anything reaches here.
            log.warn("Listener for {} skipped - database unavailable: {}",
                    event.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String guildIdOf(GenericEvent event) {
        if (event instanceof SlashCommandInteractionEvent) {
            return null;
        }
        if (event instanceof MessageReceivedEvent e) {
            return e.isFromGuild() ? e.getGuild().getId() : null;
        }
        if (event instanceof ButtonInteractionEvent e) {
            return e.getGuild() == null ? null : e.getGuild().getId();
        }
        if (event instanceof StringSelectInteractionEvent e) {
            return e.getGuild() == null ? null : e.getGuild().getId();
        }
        if (event instanceof CommandAutoCompleteInteractionEvent e) {
            return e.getGuild() == null ? null : e.getGuild().getId();
        }
        return null;
    }

    @Override
    public void register(Object listener) {
        delegate.register(listener);
    }

    @Override
    public void unregister(Object listener) {
        delegate.unregister(listener);
    }

    @Override
    public List<Object> getRegisteredListeners() {
        return delegate.getRegisteredListeners();
    }
}
