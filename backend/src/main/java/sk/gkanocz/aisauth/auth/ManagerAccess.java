package sk.gkanocz.aisauth.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @RestController} method as guild-scoped: the caller must be a manager of (or
 * super admin over) the guild in question. Purely documentation - it enforces nothing on its own,
 * the real gate is still the method's own {@code guildAccessService.assertCanManageGuild(...)}/
 * {@code canManageGuild(...)} call. What this buys: {@link ControllerAuthorizationMarkerTest} fails
 * the build if a new controller method has none of {@link ManagerAccess}, {@link SuperAdminAccess}
 * or {@link PublicToAuthenticated} - so forgetting to even think about auth on a new endpoint is a
 * compile-time-adjacent (test-time) failure instead of a silent runtime gap.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ManagerAccess {
}
