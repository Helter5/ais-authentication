package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.directory.VerificationProperties;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.verification.VerifiedUser;
import sk.gkanocz.aisauth.verification.VerifiedUserRepository;
import sk.gkanocz.aisauth.warn.Warn;
import sk.gkanocz.aisauth.warn.WarnService;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Ports the old bot's info/exportrole/say/serverinfo/user utility commands. */
@Slf4j
@Component
@RequiredArgsConstructor
class UtilityCommandListener {

    private static final Map<String, String> ACTION_LABELS =
            Map.of("kick", "Kick", "ban", "Ban", "timeout", "Timeout (24h)", "none", "None");
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final GuildSettingsService guildSettingsService;
    private final VerificationProperties verificationProperties;
    private final WarnService warnService;
    private final VerifiedUserRepository verifiedUserRepository;
    private final LogRoutingService logRoutingService;

    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        switch (event.getName()) {
            case "info" -> handleInfo(event, ephemeralOverride);
            case "exportrole" -> handleExportRole(event, ephemeralOverride);
            case "say" -> handleSay(event, ephemeralOverride);
            case "serverinfo" -> handleServerInfo(event, ephemeralOverride);
            case "user" -> handleUser(event, ephemeralOverride);
            default -> {
            }
        }
    }

    private void handleInfo(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        boolean ephemeral = ephemeralOverride == null ? true : ephemeralOverride;
        Guild guild = event.getGuild();
        GuildSettings settings = guildSettingsService.getOrCreate(guild.getId());

        String warnLimitInfo = warnService.getThresholds(guild.getId()).stream()
                .map(t -> "**" + t.getWarnLimit() + " warns** → " + ACTION_LABELS.getOrDefault(t.getAction(), t.getAction()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("Not set");

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(0x0099ff)
                .setTitle("Bot Configuration")
                .setDescription("**Server:** " + guild.getName() + "\n**Server ID:** " + guild.getId())
                .addField("Allowed Faculties", String.join(", ", verificationProperties.allowedFaculties()), false)
                .addField("Required Status", verificationProperties.requiredAccountStatus(), true)
                .addField("Warn Log Channel", channelMention(
                        logRoutingService.channelIdFor(guild.getId(), LogEventType.WARN_ISSUED).orElse(null)), true)
                .addField("Verified Users", String.valueOf(verifiedUserRepository.countByGuildId(guild.getId())), true)
                .addField("Verified Role", roleMention(guild, settings.getVerifiedRoleId()), true)
                .addField("Inactive Role", roleMention(guild, settings.getInactiveRoleId()), true)
                .addField("Warn Limit Settings", warnLimitInfo, false)
                .addField("Hacked Account Trap Channel", channelMention(settings.getSpamTrapChannelId()), true)
                .addField("Spam Log Channel", channelMention(
                        logRoutingService.channelIdFor(guild.getId(), LogEventType.HACKED_ACCOUNT_TRAP_TRIGGERED).orElse(null)), true)
                .addField("Spam Delete Interval", settings.getSpamDeleteInterval() + " min", true)
                .setThumbnail(guild.getIconUrl())
                .setFooter("SU FE")
                .setTimestamp(java.time.Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(ephemeral).queue();
    }

    private String channelMention(String channelId) {
        return channelId == null ? "Not set" : "<#" + channelId + ">";
    }

    private String roleMention(Guild guild, String roleId) {
        if (roleId == null) {
            return "Not set";
        }
        return guild.getRoleById(roleId) != null ? "<@&" + roleId + ">" : "Role ID: " + roleId + " (not found)";
    }

    private void handleExportRole(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        boolean ephemeral = ephemeralOverride == null ? false : ephemeralOverride;
        Role targetRole = event.getOption("role").getAsRole();
        OptionMapping onlyOption = event.getOption("only");
        Boolean only = onlyOption == null ? null : onlyOption.getAsBoolean();

        event.reply("Fetching members with <@&" + targetRole.getId() + ">...").setEphemeral(ephemeral).queue();

        try {
            List<Member> members = event.getGuild().loadMembers().get();
            List<Member> withRole = members.stream().filter(m -> hasRole(m, targetRole.getId())).toList();

            List<Member> filtered;
            if (Boolean.TRUE.equals(only)) {
                filtered = withRole.stream().filter(m -> nonEveryoneRoleCount(m, event.getGuild()) == 1).toList();
            } else if (Boolean.FALSE.equals(only)) {
                filtered = withRole.stream().filter(m -> nonEveryoneRoleCount(m, event.getGuild()) > 1).toList();
            } else {
                filtered = withRole;
            }

            if (filtered.isEmpty()) {
                String modeStr = Boolean.TRUE.equals(only) ? "with only that role"
                        : Boolean.FALSE.equals(only) ? "with additional roles" : "with that role";
                event.getHook().editOriginal("No members found " + modeStr + ".").queue();
                return;
            }

            StringBuilder content = new StringBuilder();
            for (Member member : filtered) {
                content.append(member.getUser().getName()).append(", ").append(member.getId()).append("\n");
            }
            String modeLabel = Boolean.TRUE.equals(only) ? " (role only)"
                    : Boolean.FALSE.equals(only) ? " (with additional roles)" : "";
            String fileName = "role_export_" + targetRole.getName() + "_" + System.currentTimeMillis() + ".txt";

            event.getHook().editOriginal("Found **" + filtered.size() + "** member(s) with <@&" + targetRole.getId() + ">" + modeLabel + ".")
                    .setFiles(FileUpload.fromData(content.toString().getBytes(StandardCharsets.UTF_8), fileName))
                    .queue();
        } catch (Exception e) {
            log.error("exportrole command error", e);
            event.getHook().editOriginal("Error generating export: " + e.getMessage()).queue();
        }
    }

    private boolean hasRole(Member member, String roleId) {
        return member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId));
    }

    private long nonEveryoneRoleCount(Member member, Guild guild) {
        return member.getRoles().stream().filter(r -> !r.getId().equals(guild.getId())).count();
    }

    private void handleSay(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        boolean ephemeral = ephemeralOverride == null ? true : ephemeralOverride;
        String message = event.getOption("message").getAsString();
        OptionMapping channelOption = event.getOption("channel");
        TextChannel targetChannel = channelOption != null
                ? channelOption.getAsChannel().asTextChannel() : event.getChannel().asTextChannel();

        if (!event.getGuild().getSelfMember().hasPermission(targetChannel, Permission.MESSAGE_SEND)) {
            event.reply("I don't have permission to send messages in <#" + targetChannel.getId() + ">.")
                    .setEphemeral(ephemeral).queue();
            return;
        }

        try {
            targetChannel.sendMessage(message).complete();
        } catch (Exception e) {
            log.error("say command error", e);
            event.reply("Failed to send message: " + e.getMessage()).setEphemeral(ephemeral).queue();
            return;
        }

        event.reply("Message sent in <#" + targetChannel.getId() + ">.").setEphemeral(ephemeral).queue();
    }

    private void handleServerInfo(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        boolean ephemeral = ephemeralOverride != null && ephemeralOverride;
        event.deferReply(ephemeral).queue();
        Guild guild = event.getGuild();

        try {
            Member owner = guild.retrieveOwner().complete();
            List<GuildChannel> channels = guild.getChannels();
            long total = channels.size();

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(0x0099ff)
                    .setTitle(guild.getName() + " - Server Information")
                    .setThumbnail(guild.getIconUrl())
                    .addField("Owner", owner.getUser().getName(), false)
                    .addField("Roles (including @everyone)", String.valueOf(guild.getRoles().size()), false)
                    .addField("Total channels (voice + text + category + forum + news + stage + media)",
                            String.valueOf(total), false)
                    .setFooter("Server ID: " + guild.getId() + " • Created")
                    .setTimestamp(guild.getTimeCreated());
            if (guild.getBannerUrl() != null) {
                embed.setImage(guild.getBannerUrl());
            }

            event.getHook().editOriginalEmbeds(embed.build()).queue();
        } catch (Exception e) {
            log.error("serverinfo command error", e);
            event.getHook().editOriginal("An error occurred: " + e.getMessage()).queue();
        }
    }

    private void handleUser(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        boolean ephemeral = ephemeralOverride != null && ephemeralOverride;
        event.deferReply(ephemeral).queue();
        Guild guild = event.getGuild();
        User targetUser = event.getOption("user").getAsUser();

        try {
            Member member;
            try {
                member = guild.retrieveMemberById(targetUser.getId()).complete();
            } catch (Exception e) {
                member = null;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(member != null ? member.getColorRaw() : 0x5865f2)
                    .setAuthor(targetUser.getName(), null, targetUser.getEffectiveAvatarUrl())
                    .setThumbnail(member != null ? member.getEffectiveAvatarUrl() : targetUser.getEffectiveAvatarUrl())
                    .setFooter("ID: " + targetUser.getId() + " • Requested by " + event.getUser().getName(),
                            event.getUser().getEffectiveAvatarUrl())
                    .setTimestamp(java.time.Instant.now())
                    .addField("Account Created", targetUser.getTimeCreated().format(TS_FORMAT), false);

            if (member != null) {
                embed.addField("Joined Server", member.getTimeJoined().format(TS_FORMAT), false);
                if (member.getNickname() != null) {
                    embed.addField("Nickname", member.getNickname(), true);
                }
                if (member.getTimeBoosted() != null) {
                    embed.addField("Boosting Since", member.getTimeBoosted().format(TS_FORMAT), false);
                }
                if (member.isPending()) {
                    embed.addField("Pending", "⚠️ Has not accepted server rules yet", false);
                }
                List<String> roles = member.getRoles().stream().map(r -> "<@&" + r.getId() + ">").toList();
                if (!roles.isEmpty()) {
                    String roleStr = roles.size() > 20
                            ? String.join(" ", roles.subList(0, 20)) + " *(+" + (roles.size() - 20) + " more)*"
                            : String.join(" ", roles);
                    embed.addField("Roles (" + roles.size() + ")", roleStr, false);
                } else {
                    embed.addField("Roles", "*None*", false);
                }
            } else {
                embed.addField("Status", "⚠️ Not in server", false);
            }

            VerifiedUser verifiedUser = verifiedUserRepository.findByDiscordIdAndGuildId(targetUser.getId(), guild.getId()).orElse(null);
            if (verifiedUser != null) {
                StringBuilder verFields = new StringBuilder("**AIS ID:** ").append(verifiedUser.getAisId());
                if (verifiedUser.getEmail() != null) {
                    verFields.append("\n**Email:** ").append(verifiedUser.getEmail());
                }
                verFields.append("\n**Verified:** ").append(verifiedUser.getVerifiedAt().format(TS_FORMAT));
                embed.addField("✅ Verified", verFields.toString(), false);
            } else {
                embed.addField("❌ Not Verified", "Not in the verified users database.", false);
            }

            long warnCount = warnService.countWarns(targetUser.getId(), guild.getId());
            if (warnCount > 0) {
                List<Warn> warns = warnService.getWarns(targetUser.getId(), guild.getId());
                StringBuilder warnLines = new StringBuilder();
                for (int i = 0; i < Math.min(3, warns.size()); i++) {
                    Warn warn = warns.get(i);
                    if (i > 0) {
                        warnLines.append("\n");
                    }
                    warnLines.append("**").append(i + 1).append(".** ").append(warn.getReason())
                            .append(" — ").append(warn.getCreatedAt().format(TS_FORMAT));
                }
                if (warnCount > 3) {
                    warnLines.append("\n*…and ").append(warnCount - 3).append(" more*");
                }
                embed.addField("⚠️ Warnings (" + warnCount + ")", warnLines.toString(), false);
            } else {
                embed.addField("Warnings", "0", true);
            }

            if (targetUser.isBot()) {
                embed.addField("Bot", "🤖 This is a bot account", true);
            }

            event.getHook().editOriginalEmbeds(embed.build()).queue();
        } catch (Exception e) {
            log.error("[/user] Error", e);
            event.getHook().editOriginal("Error: " + e.getMessage()).queue();
        }
    }
}
