package sk.gkanocz.aisauth.semester;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
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
}
