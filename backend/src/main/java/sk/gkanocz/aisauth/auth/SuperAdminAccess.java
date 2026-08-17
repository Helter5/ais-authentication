package sk.gkanocz.aisauth.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @RestController} method as super-admin-only: the caller must be a configured
 * super admin. Purely documentation - it enforces nothing on its own, the real gate is still the
 * method's own {@code guildAccessService.assertSuperAdmin(...)}/{@code isSuperAdmin(...)} call.
 * See {@link ManagerAccess} for why this marker exists.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SuperAdminAccess {
}
