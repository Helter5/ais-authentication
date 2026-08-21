package sk.gkanocz.aisauth.semester;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import sk.gkanocz.aisauth.semester.SwitchSemesterSettings.AdditionalChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemesterVisibilityServiceTest {

    @Mock
    private Guild guild;
    @Mock
    private Category category;
    @Mock
    private Role publicRole;
    @Mock
    private Role memberRole;
    @Mock
    private PermissionOverride memberOverride;
    @Mock
    private PermissionOverrideAction publicRoleAction;
    @Mock
    private PermissionOverrideAction memberRoleAction;

    private final SemesterVisibilityService service = new SemesterVisibilityService();

    @BeforeEach
    void setUp() {
        lenient().when(guild.getPublicRole()).thenReturn(publicRole);
        lenient().when(publicRole.isPublicRole()).thenReturn(true);
    }

    @Test
    void categoryNotFoundIsCountedAsAnError() {
        when(guild.getCategoryById("missing-category")).thenReturn(null);

        SemesterVisibilityService.Result result = service.apply(guild, List.of("missing-category"), true, true);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.categoriesUpdated()).isZero();
        assertThat(result.logs()).anyMatch(log -> log.contains("not found in guild"));
    }

    @Test
    void appliesVisibilityToCategoryAndItsRoleOverrides() {
        when(guild.getCategoryById("cat-1")).thenReturn(category);
        when(category.getName()).thenReturn("General");
        when(category.getChannels()).thenReturn(List.of());
        when(category.getRolePermissionOverrides()).thenReturn(List.of(memberOverride));
        when(memberOverride.getRole()).thenReturn(memberRole);
        when(memberRole.isPublicRole()).thenReturn(false);
        when(memberRole.getName()).thenReturn("Members");

        when(category.upsertPermissionOverride(publicRole)).thenReturn(publicRoleAction);
        when(publicRoleAction.grant(any(net.dv8tion.jda.api.Permission.class))).thenReturn(publicRoleAction);
        when(category.upsertPermissionOverride(memberRole)).thenReturn(memberRoleAction);
        when(memberRoleAction.grant(any(net.dv8tion.jda.api.Permission.class))).thenReturn(memberRoleAction);

        SemesterVisibilityService.Result result = service.apply(guild, List.of("cat-1"), true, true);

        assertThat(result.success()).isTrue();
        assertThat(result.categoriesUpdated()).isEqualTo(1);
        assertThat(result.channelsUpdated()).isZero();
        assertThat(result.rolesUpdated()).isEqualTo(1);
        assertThat(result.errors()).isZero();
        verify(publicRoleAction).complete();
        verify(memberRoleAction).complete();
    }

    @Test
    void channelNotFoundIsCountedAsAnError() {
        when(guild.getGuildChannelById("missing-channel")).thenReturn(null);

        SemesterVisibilityService.Result result = service.applyChannels(
                guild, List.of(new AdditionalChannel("missing-channel", "gone", true, false)));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.channelsUpdated()).isZero();
        assertThat(result.logs()).anyMatch(log -> log.contains("not found in guild"));
    }

    @Test
    void visibleAppliesToEveryExistingRoleOverrideUnconditionally(@org.mockito.Mock TextChannel channel) {
        when(guild.getGuildChannelById("chan-1")).thenReturn(channel);
        when(channel.getName()).thenReturn("zs-volitelne");
        when(channel.getRolePermissionOverrides()).thenReturn(List.of(memberOverride));
        when(memberOverride.getRole()).thenReturn(memberRole);
        when(memberRole.isPublicRole()).thenReturn(false);
        when(memberRole.getName()).thenReturn("Members");

        when(channel.upsertPermissionOverride(publicRole)).thenReturn(publicRoleAction);
        when(publicRoleAction.deny(any(net.dv8tion.jda.api.Permission.class))).thenReturn(publicRoleAction);
        when(channel.upsertPermissionOverride(memberRole)).thenReturn(memberRoleAction);
        when(memberRoleAction.grant(any(net.dv8tion.jda.api.Permission.class))).thenReturn(memberRoleAction);

        // visible=true, everyoneViewChannel left unset (defaults false) - the exact "show it for
        // everybody but keep @everyone itself hidden" case this split exists for. Unlike
        // SemesterVisibilityService#apply for categories, the two flags are independent here, not
        // one gating the other.
        SemesterVisibilityService.Result result = service.applyChannels(
                guild, List.of(new AdditionalChannel("chan-1", "zs-volitelne", true, null)));

        assertThat(result.success()).isTrue();
        assertThat(result.rolesUpdated()).isEqualTo(1);
        verify(publicRoleAction).deny(net.dv8tion.jda.api.Permission.VIEW_CHANNEL);
        verify(publicRoleAction).complete();
        verify(memberRoleAction).grant(net.dv8tion.jda.api.Permission.VIEW_CHANNEL);
        verify(memberRoleAction).complete();
    }

    @Test
    void everyoneViewChannelTrueAlsoShowsItToEveryone(@org.mockito.Mock TextChannel channel) {
        when(guild.getGuildChannelById("chan-2")).thenReturn(channel);
        when(channel.getName()).thenReturn("ls-volitelne");
        when(channel.getRolePermissionOverrides()).thenReturn(List.of());
        when(channel.upsertPermissionOverride(publicRole)).thenReturn(publicRoleAction);
        when(publicRoleAction.grant(any(net.dv8tion.jda.api.Permission.class))).thenReturn(publicRoleAction);

        SemesterVisibilityService.Result result = service.applyChannels(
                guild, List.of(new AdditionalChannel("chan-2", "ls-volitelne", true, true)));

        assertThat(result.success()).isTrue();
        verify(publicRoleAction).grant(net.dv8tion.jda.api.Permission.VIEW_CHANNEL);
        verify(publicRoleAction).complete();
    }

    @Test
    void everyoneViewChannelIsIndependentOfVisibleEvenWhenHiding(@org.mockito.Mock TextChannel channel) {
        when(guild.getGuildChannelById("chan-3")).thenReturn(channel);
        when(channel.getName()).thenReturn("zs-volitelne");
        when(channel.getRolePermissionOverrides()).thenReturn(List.of(memberOverride));
        when(memberOverride.getRole()).thenReturn(memberRole);
        when(memberRole.isPublicRole()).thenReturn(false);
        when(memberRole.getName()).thenReturn("Members");

        when(channel.upsertPermissionOverride(publicRole)).thenReturn(publicRoleAction);
        when(publicRoleAction.grant(any(net.dv8tion.jda.api.Permission.class))).thenReturn(publicRoleAction);
        when(channel.upsertPermissionOverride(memberRole)).thenReturn(memberRoleAction);
        when(memberRoleAction.deny(any(net.dv8tion.jda.api.Permission.class))).thenReturn(memberRoleAction);

        // visible=false (every other role hidden) + everyoneViewChannel=true (@everyone still sees
        // it) - a deliberately valid, meaningful combination unlike category apply()'s
        // "everyoneVisible = visible && everyoneViewChannel" (there, hiding always forces @everyone
        // hidden too).
        SemesterVisibilityService.Result result = service.applyChannels(
                guild, List.of(new AdditionalChannel("chan-3", "zs-volitelne", false, true)));

        assertThat(result.success()).isTrue();
        assertThat(result.rolesUpdated()).isEqualTo(1);
        verify(publicRoleAction).grant(net.dv8tion.jda.api.Permission.VIEW_CHANNEL);
        verify(publicRoleAction).complete();
        verify(memberRoleAction).deny(net.dv8tion.jda.api.Permission.VIEW_CHANNEL);
        verify(memberRoleAction).complete();
    }
}
