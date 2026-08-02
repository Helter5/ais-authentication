package sk.gkanocz.aisauth.ticket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

/**
 * Handles the ticket_* button custom-ids posted by TicketService / HackedAccountTrapListener:
 * close, close_confirm, close_cancel, open, delete, transcript. Mirrors handleTicketButtonInteraction
 * from the old bot's IncidentTicket.js.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketButtonListener extends ListenerAdapter {

    private final IncidentTicketRepository incidentTicketRepository;
    private final TicketService ticketService;
    private final LogRoutingService logRoutingService;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("ticket_")) {
            return;
        }
        if (event.getGuild() == null) {
            return;
        }

        String[] parts = event.getComponentId().split(":", 2);
        String action = parts[0];
        String channelId = parts.length > 1 ? parts[1] : "";

        Optional<IncidentTicket> ticketOpt = incidentTicketRepository.findById(channelId);
        if (ticketOpt.isEmpty() || !ticketOpt.get().getGuildId().equals(event.getGuild().getId())) {
            event.reply("This ticket no longer exists.").setEphemeral(true).queue();
            return;
        }
        IncidentTicket ticket = ticketOpt.get();

        Member member = event.getMember();
        if (member == null || !ticketService.isTicketManager(ticket.getGuildId(), member)) {
            event.reply("You do not have permission to manage this ticket.").setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        try {
            switch (action) {
                case "ticket_close" -> handleClose(event, ticket);
                case "ticket_close_cancel" -> event.editMessage("Close cancelled.").setComponents(List.of()).queue();
                case "ticket_close_confirm" -> handleCloseConfirm(event, ticket, channel);
                case "ticket_open" -> handleOpen(event, ticket, channel);
                case "ticket_delete" -> handleDelete(event, channel);
                case "ticket_transcript" -> handleTranscript(event, ticket, channel);
                default -> { }
            }
        } catch (Exception e) {
            log.error("IncidentTicket: Failed to handle \"{}\"", action, e);
            String message = "Something went wrong handling this ticket action.";
            if (event.isAcknowledged()) {
                event.getHook().sendMessage(message).setEphemeral(true).queue();
            } else {
                event.reply(message).setEphemeral(true).queue();
            }
        }
    }

    private void handleClose(ButtonInteractionEvent event, IncidentTicket ticket) {
        if (ticketService.closedCategoryId(ticket.getGuildId()) == null) {
            event.reply("Set a \"Category on close\" in the Hacked Account Trap module settings before closing tickets.")
                    .setEphemeral(true).queue();
            return;
        }
        event.reply("Are you sure you would like to close this ticket?")
                .addActionRow(ticketService.closeConfirmButtons(ticket.getChannelId()))
                .queue();
    }

    private void handleCloseConfirm(ButtonInteractionEvent event, IncidentTicket ticket, TextChannel channel) {
        String closedCategoryId = ticketService.closedCategoryId(ticket.getGuildId());
        if (closedCategoryId == null) {
            event.editMessage("Set a \"Category on close\" in the Hacked Account Trap module settings before closing tickets.")
                    .setComponents(List.of()).queue();
            return;
        }
        event.deferEdit().queue();

        List<TranscriptMessage> transcript = ticketService.buildTranscript(channel);
        channel.upsertPermissionOverride(memberOrThrow(event, ticket.getUserId()))
                .deny(Permission.VIEW_CHANNEL).queue();
        Category closedCategory = event.getGuild().getCategoryById(closedCategoryId);
        if (closedCategory != null && !closedCategory.getId().equals(channelParentId(channel))) {
            channel.getManager().setParent(closedCategory).queue(s -> { }, f -> log.error(
                    "IncidentTicket: Failed to move channel to closed category: {}", f.getMessage()));
        }

        ticket.close(event.getUser().getId(), ticketService.writeTranscript(transcript));
        incidentTicketRepository.save(ticket);

        event.getHook().editOriginal("Ticket closed by " + event.getUser().getName() + " (<@" + event.getUser().getId() + ">).")
                .setComponents(List.of()).queue();
        ticketService.postClosedControls(channel, ticket);
    }

    private void handleOpen(ButtonInteractionEvent event, IncidentTicket ticket, TextChannel channel) {
        if (ticketService.includeUserSetting(ticket.getGuildId())) {
            channel.upsertPermissionOverride(memberOrThrow(event, ticket.getUserId()))
                    .grant(Permission.VIEW_CHANNEL).queue();
        } else {
            var override = channel.getPermissionOverride(memberOrThrow(event, ticket.getUserId()));
            if (override != null) {
                override.delete().queue();
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
    }

    private void handleDelete(ButtonInteractionEvent event, TextChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0xFF0000))
                .setDescription("Ticket will be deleted in a few seconds.");
        event.replyEmbeds(embed.build()).queue();
        channel.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS, s -> { },
                f -> log.error("IncidentTicket: Failed to delete channel: {}", f.getMessage()));
    }

    private void handleTranscript(ButtonInteractionEvent event, IncidentTicket ticket, TextChannel channel) {
        if (ticket.getTranscript() == null) {
            event.reply("No transcript yet — close the ticket first.").setEphemeral(true).queue();
            return;
        }
        if (logRoutingService.channelIdFor(ticket.getGuildId(), LogEventType.TICKET_TRANSCRIPT_SAVED).isEmpty()) {
            event.reply("Set a Transcript Log channel in Settings → Log Channels before saving transcripts.")
                    .setEphemeral(true).queue();
            return;
        }
        String url = ticketService.frontendUrl() + "/tickets/" + ticket.getChannelId() + "?guildId=" + ticket.getGuildId();
        boolean saved = ticketService.saveTranscriptToLogChannel(channel, ticket, url);
        event.reply(saved
                ? "Transcript saved. " + url
                : "Failed to save the transcript — check that the configured Transcript Log channel still exists and the bot can post there.")
                .setEphemeral(true).queue();
    }

    private Member memberOrThrow(ButtonInteractionEvent event, String userId) {
        Member member = event.getGuild().retrieveMemberById(userId).complete();
        if (member == null) {
            throw new IllegalStateException("Ticket owner is no longer a member of this server");
        }
        return member;
    }

    private String channelParentId(TextChannel channel) {
        Category parent = channel.getParentCategory();
        return parent == null ? null : parent.getId();
    }
}
