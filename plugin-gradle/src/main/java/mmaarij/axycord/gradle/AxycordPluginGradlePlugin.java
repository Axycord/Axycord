package mmaarij.axycord.gradle;

import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import kotlin.Unit;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class AxycordPluginGradlePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AxycordPluginExtension extension = project.getExtensions().create(
            "axycordPlugin",
            AxycordPluginExtension.class
        );
        extension.getDescription().convention("");
        extension.getAxycordVersionMinimum().convention(1);
        extension.getAxycordVersionMaximumExclusive().convention(2);
        extension.getPriority().convention(0);
        extension.getUsesResources().convention(false);
        extension.getLoadMode().convention("ASYNC");
        extension.getRequirements().convention(java.util.List.of());

        project.getPlugins().withId("com.android.application", ignored ->
            project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class)
                .finalizeDsl(android -> {
                    configureManifest(android, extension);
                    return Unit.INSTANCE;
                })
        );
    }

    private static void configureManifest(ApplicationExtension android, AxycordPluginExtension extension) {
        String id = extension.getId().get();
        String entrypoint = extension.getEntrypoint().get();
        if (!id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("axycordPlugin.id is invalid");
        if (entrypoint.isBlank()) throw new IllegalArgumentException("axycordPlugin.entrypoint must not be blank");
        String loadMode = extension.getLoadMode().get();
        if (!loadMode.equals("ASYNC") && !loadMode.equals("BLOCKING")) {
            throw new IllegalArgumentException("axycordPlugin load mode must be ASYNC or BLOCKING");
        }
        int axycordMinimum = extension.getAxycordVersionMinimum().get();
        int axycordMaximum = extension.getAxycordVersionMaximumExclusive().get();
        if (axycordMinimum < 1 || axycordMaximum <= axycordMinimum) {
            throw new IllegalArgumentException("axycordPlugin app version range is invalid");
        }
        Set<String> requirementIds = new HashSet<>();
        for (String requirement : extension.getRequirements().get()) {
            String requirementId = requirement.substring(0, requirement.indexOf(':'));
            if (!requirementIds.add(requirementId)) {
                throw new IllegalArgumentException("axycordPlugin requirements must be unique");
            }
        }

        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("axycordPluginId", id);
        placeholders.put("axycordPluginEntrypoint", entrypoint);
        placeholders.put("axycordPluginDescription", extension.getDescription().get());
        placeholders.put("axycordPluginVersionMinimum", axycordMinimum);
        placeholders.put("axycordPluginVersionMaximumExclusive", axycordMaximum);
        placeholders.put("axycordPluginPriority", extension.getPriority().get());
        placeholders.put("axycordPluginUsesResources", extension.getUsesResources().get());
        placeholders.put("axycordPluginLoadMode", loadMode);
        placeholders.put("axycordPluginRequirements", String.join(";", extension.getRequirements().get()));

        android.getDefaultConfig()
            .getManifestPlaceholders()
            .putAll(placeholders);
    }
}
