package sk.gkanocz.aisauth.auth;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code @RestController} method mapped to an HTTP verb must declare exactly what its auth
 * model is - {@link ManagerAccess}, {@link SuperAdminAccess} or {@link PublicToAuthenticated} - or
 * this test fails, listing the offending method. The markers don't enforce anything at runtime
 * (the real gate stays the method's own {@code guildAccessService} call); this test is what turns
 * "a new endpoint forgot to think about auth" from a silent runtime gap into a build failure.
 *
 * <p>Fixing a failure here means picking the marker that matches what the method's body actually
 * does, not just adding one to make the test pass - see each annotation's javadoc.
 */
class ControllerAuthorizationMarkerTest {

    private static final String BASE_PACKAGE = "sk.gkanocz.aisauth";

    @SuppressWarnings("unchecked")
    private static final List<Class<? extends Annotation>> MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    private static final Set<Class<? extends Annotation>> MARKER_ANNOTATIONS = Set.of(
            ManagerAccess.class, SuperAdminAccess.class, PublicToAuthenticated.class);

    @Test
    void everyMappedControllerMethodDeclaresItsAuthModel() throws ClassNotFoundException {
        List<String> unmarked = new ArrayList<>();

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (var candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controllerClass = Class.forName(candidate.getBeanClassName());
            boolean classLevelMapping = controllerClass.isAnnotationPresent(RequestMapping.class);

            for (Method method : controllerClass.getDeclaredMethods()) {
                boolean isMapped = MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent)
                        || (classLevelMapping && method.isAnnotationPresent(RequestMapping.class));
                if (!isMapped) {
                    continue;
                }
                boolean hasMarker = MARKER_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent);
                if (!hasMarker) {
                    unmarked.add(controllerClass.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(unmarked)
                .as("Every @RestController method mapped to an HTTP verb needs @ManagerAccess, "
                        + "@SuperAdminAccess or @PublicToAuthenticated declaring its auth model. Missing on: %s",
                        unmarked)
                .isEmpty();
    }
}
