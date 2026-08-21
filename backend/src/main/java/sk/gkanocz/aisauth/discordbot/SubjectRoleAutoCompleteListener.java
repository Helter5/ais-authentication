package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.semester.SemesterOperationService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Suggestions for /pridatpredmet's predmetN options - Discord's ROLE option type shows every role
 * in the guild with no way to filter it, so the only way to make the picker only offer the
 * admin-approved subject roles is a STRING option with autocomplete. Suggestions are filtered
 * server-side to the allowlist and to exclude roles the invoking member already holds; the actual
 * value submitted is still re-validated against the allowlist in SubjectRoleSlashCommandListener,
 * since autocomplete is a UI hint, not an enforced constraint - a user can still type/submit
 * arbitrary text past it.
 */
@Component
@RequiredArgsConstructor
public class SubjectRoleAutoCompleteListener extends ListenerAdapter {

    private static final int MAX_CHOICES = 25;

    private final AdminSettingsService adminSettingsService;
    /** @Lazy: same startup-cycle guard as SubjectRoleSlashCommandListener - see its javadoc. */
    @Lazy
    private final SemesterOperationService semesterOperationService;

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!SubjectRoleSettings.COMMAND_NAME.equals(event.getName())) {
            return;
        }
        Guild guild = event.getGuild();
        if (guild == null) {
            event.replyChoices(List.of()).queue();
            return;
        }

        String semesterType = semesterOperationService.currentSemesterType(guild.getId());
        Set<String> allowedRoleIds = SubjectRoleSettings.allowedRoleIds(adminSettingsService, guild.getId(), semesterType);
        Member member = event.getMember();
        Set<String> memberRoleIds = member == null
                ? Set.of()
                : member.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
        String typed = event.getFocusedOption().getValue().toLowerCase();

        List<Command.Choice> choices = guild.getRoles().stream()
                .filter(role -> allowedRoleIds.contains(role.getId()))
                .filter(role -> !memberRoleIds.contains(role.getId()))
                .filter(role -> role.getName().toLowerCase().contains(typed))
                .limit(MAX_CHOICES)
                .map(role -> new Command.Choice(role.getName(), role.getId()))
                .toList();

        event.replyChoices(choices).queue();
    }
}
