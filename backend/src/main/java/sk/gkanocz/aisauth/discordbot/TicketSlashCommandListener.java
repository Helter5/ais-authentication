package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.ticket.IncidentTicket;
import sk.gkanocz.aisauth.ticket.IncidentTicketRepository;
import sk.gkanocz.aisauth.ticket.TicketService;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Slash-command equivalents of the ticket_* buttons in TicketButtonListener, so tickets can be
 * managed without hunting for the button (e.g. after it has scrolled out of view): /ticketclose,
 * /ticketreopen, /ticketdelete, /ticketrecap. Only usable inside an actual ticket channel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TicketSlashCommandListener {

    private final IncidentTicketRepository incidentTicketRepository;
    private final TicketService ticketService;
    private final LogRoutingService logRoutingService;

    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        switch (event.getName()) {
            case "ticketclose" -> handleClose(event);
            case "ticketreopen" -> handleReopen(event);
            case "ticketdelete" -> handleDelete(event);
            case "ticketrecap" -> handleRecap(event);
            default -> {
            }
        }
    }

    private Optional<IncidentTicket> requireTicket(SlashCommandInteractionEvent event) {
        Optional<IncidentTicket> ticket = incidentTicketRepository.findById(event.getChannel().getId())
                .filter(t -> t.getGuildId().equals(event.getGuild().getId()));
        if (ticket.isEmpty()) {
            event.reply("This channel is not a ticket.").setEphemeral(true).queue();
        }
        return ticket;
    }

    private boolean requireManager(SlashCommandInteractionEvent event, IncidentTicket ticket) {
        Member member = event.getMember();
        if (member == null || !ticketService.isTicketManager(ticket.getGuildId(), member)) {
            event.reply("You do not have permission to manage this ticket.").setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private void handleClose(SlashCommandInteractionEvent event) {
        requireTicket(event).ifPresent(ticket -> {
            if (!requireManager(event, ticket)) {
                return;
            }
            if (!ticket.isOpen()) {
                event.reply("This ticket is already closed.").setEphemeral(true).queue();
                return;
            }
            if (ticketService.closedCategoryId(ticket.getGuildId()) == null) {
                event.reply("Set a \"Category on close\" in the Hacked Account Trap module settings before closing tickets.")
                        .setEphemeral(true).queue();
                return;
            }
            event.reply("Are you sure you would like to close this ticket?")
                    .addActionRow(ticketService.closeConfirmButtons(ticket.getChannelId()))
                    .queue();
        });
    }

    private void handleReopen(SlashCommandInteractionEvent event) {
        requireTicket(event).ifPresent(ticket -> {
            if (!requireManager(event, ticket)) {
                return;
            }
            if (ticket.isOpen()) {
                event.reply("This ticket is already open.").setEphemeral(true).queue();
                return;
            }
            TextChannel channel = event.getChannel().asTextChannel();
            Member owner = event.getGuild().retrieveMemberById(ticket.getUserId()).complete();
            if (owner != null) {
                if (ticketService.includeUserSetting(ticket.getGuildId())) {
                    channel.upsertPermissionOverride(owner).grant(Permission.VIEW_CHANNEL).queue();
                } else {
                    var override = channel.getPermissionOverride(owner);
                    if (override != null) {
                        override.delete().queue();
                    }
                }
            }
            String openCategoryId = ticketService.openCategoryId(ticket.getGuildId());
            if (openCategoryId != null) {
                Category openCategory = event.getGuild().getCategoryById(openCategoryId);
                if (openCategory != null && !openCategory.getId().equals(channelParentId(channel))) {
                    channel.getManager().setParent(openCategory).queue(s -> { }, f -> log.error(
                            "IncidentTicket: Failed to move channel to open category: {}", f.getMessage()));
                }
            }
            event.reply("Ticket reopened by " + event.getUser().getName() + " (<@" + event.getUser().getId() + ">).").queue();
            ticketService.postTicketControls(channel, ticket.getGuildId(), ticket.getUserId());
        });
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        requireTicket(event).ifPresent(ticket -> {
            if (!requireManager(event, ticket)) {
                return;
            }
            TextChannel channel = event.getChannel().asTextChannel();
            event.reply("Ticket will be deleted in a few seconds.").queue();
            channel.delete().queueAfter(5, TimeUnit.SECONDS, s -> { },
                    f -> log.error("IncidentTicket: Failed to delete channel: {}", f.getMessage()));
        });
    }

    private void handleRecap(SlashCommandInteractionEvent event) {
        requireTicket(event).ifPresent(ticket -> {
            if (!requireManager(event, ticket)) {
                return;
            }
            if (ticket.getTranscript() == null) {
                event.reply("No transcript yet — close the ticket first.").setEphemeral(true).queue();
                return;
            }
            if (logRoutingService.channelIdFor(ticket.getGuildId(), LogEventType.TICKET_TRANSCRIPT_SAVED).isEmpty()) {
                event.reply("Set a Transcript Log channel in Settings → Log Channels before saving transcripts.")
                        .setEphemeral(true).queue();
                return;
            }
            TextChannel channel = event.getChannel().asTextChannel();
            String url = ticketService.frontendUrl() + "/tickets/" + ticket.getChannelId() + "?guildId=" + ticket.getGuildId();
            boolean saved = ticketService.saveTranscriptToLogChannel(channel, ticket, url);
            event.reply(saved
                    ? "Transcript saved. " + url
                    : "Failed to save the transcript — check that the configured Transcript Log channel still exists and the bot can post there.")
                    .setEphemeral(true).queue();
        });
    }

    private String channelParentId(TextChannel channel) {
        Category parent = channel.getParentCategory();
        return parent == null ? null : parent.getId();
    }
}
