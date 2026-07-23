package sk.gkanocz.aisauth.semester;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Ports SemesterService.js's setSemesterVisibilityByCategoryIds: bulk show/hide of a set of categories. */
@Slf4j
@Service
public class SemesterVisibilityService {

    public Result apply(Guild guild, List<String> categoryIds, boolean visible, boolean everyoneViewChannel) {
        List<String> logs = new ArrayList<>();
        int categoriesUpdated = 0;
        int channelsUpdated = 0;
        int rolesUpdated = 0;
        int errors = 0;
        boolean everyoneVisible = visible && everyoneViewChannel;

        for (String categoryId : categoryIds) {
            Category category = guild.getCategoryById(categoryId);
            if (category == null) {
                logs.add("WARNING: Category ID " + categoryId + " not found in guild (may have been deleted)");
                errors++;
                continue;
            }

            try {
                setViewChannel(category, guild.getPublicRole(), everyoneVisible);
                logs.add("Category \"" + category.getName() + "\": @everyone → " + (everyoneVisible ? "visible" : "hidden"));
                rolesUpdated += applyRoleOverrides(category, guild, visible, logs, "  ");
                categoriesUpdated++;
            } catch (Exception e) {
                logs.add("ERROR category \"" + category.getName() + "\": " + e.getMessage());
                errors++;
            }

            for (GuildChannel channel : category.getChannels()) {
                try {
                    setViewChannel((IPermissionContainer) channel, guild.getPublicRole(), everyoneVisible);
                    logs.add("  Channel \"" + channel.getName() + "\": @everyone → " + (everyoneVisible ? "visible" : "hidden"));
                    rolesUpdated += applyRoleOverrides((IPermissionContainer) channel, guild, visible, logs, "    ");
                    channelsUpdated++;
                } catch (Exception e) {
                    logs.add("  ERROR channel \"" + channel.getName() + "\": " + e.getMessage());
                    errors++;
                }
            }
        }

        return new Result(errors == 0, categoriesUpdated, channelsUpdated, rolesUpdated, errors, logs);
    }

    private int applyRoleOverrides(IPermissionContainer container, Guild guild, boolean visible, List<String> logs, String indent) {
        int updated = 0;
        for (PermissionOverride override : container.getRolePermissionOverrides()) {
            Role role = override.getRole();
            if (role == null || role.isPublicRole()) {
                continue;
            }
            setViewChannel(container, role, visible);
            logs.add(indent + "Role \"" + role.getName() + "\": → " + (visible ? "visible" : "hidden"));
            updated++;
        }
        return updated;
    }

    private void setViewChannel(IPermissionContainer container, Role role, boolean grant) {
        PermissionOverrideAction action = container.upsertPermissionOverride(role);
        if (grant) {
            action.grant(Permission.VIEW_CHANNEL);
        } else {
            action.deny(Permission.VIEW_CHANNEL);
        }
        action.complete();
    }

    public record Result(
            boolean success, int categoriesUpdated, int channelsUpdated, int rolesUpdated, int errors, List<String> logs) {
    }
}
