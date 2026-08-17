package sk.gkanocz.aisauth.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @RestController} method as deliberately open to any authenticated user (no
 * guild-manager or super-admin scoping) - e.g. session lifecycle endpoints, or "am I allowed to do
 * X" self-probes that answer the question rather than gate behind it. A method carrying this
 * annotation is exempt from needing an explicit guild/admin check, but the choice must be
 * deliberate: this exists specifically so "no auth check" is a declared decision, not an oversight.
 * See {@link ManagerAccess} for why this marker exists.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PublicToAuthenticated {
}
