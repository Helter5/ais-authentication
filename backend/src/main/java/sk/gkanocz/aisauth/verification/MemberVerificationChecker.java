package sk.gkanocz.aisauth.verification;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.GuildSettingsService;

/**
 * Single place to answer "is this Discord member actually verified" - requires both the
 * configured Verified role AND a matching verified_users row, so someone who only ever got the
 * role assigned manually (never went through /verify or /manualverify) doesn't count.
 */
@Component
@RequiredArgsConstructor
public class MemberVerificationChecker {

    private final GuildSettingsService guildSettingsService;
    private final VerifiedUserRepository verifiedUserRepository;

    public boolean isVerified(Guild guild, Member member) {
        String verifiedRoleId = guildSettingsService.getOrCreate(guild.getId()).getVerifiedRoleId();
        boolean hasRole = verifiedRoleId != null && member.getRoles().stream().anyMatch(r -> r.getId().equals(verifiedRoleId));
        return hasRole && verifiedUserRepository.existsByDiscordIdAndGuildId(member.getId(), guild.getId());
    }
}
