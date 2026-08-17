package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.IEventManager;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.AdminSettingsService;

import java.util.List;

/**
 * Wraps JDA's default event dispatch so every guild-scoped event (messages, button clicks,
 * select-menu choices) is dropped in one place for guilds that aren't on the "allowed_guild_ids"
 * allowlist, instead of every automod/rolemenu listener repeating the same check. Slash
 * commands are deliberately exempt here — CommandInteractionListener does its own allowlist check
 * so it can reply explaining why the command was blocked, which a silent drop at this layer can't do.
 */
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
        delegate.handle(event);
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
