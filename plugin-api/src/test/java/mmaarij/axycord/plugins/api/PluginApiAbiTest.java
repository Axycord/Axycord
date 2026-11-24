package mmaarij.axycord.plugins.api;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import kotlin.jvm.functions.Function1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginApiAbiTest {
    @Test
    public void publicModelsAreInterfacesNotConstructorAbis() {
        assertTrue(PluginManifest.class.isInterface());
        assertTrue(PluginRequirement.class.isInterface());
        assertTrue(PluginSettingsSchema.class.isInterface());
        assertTrue(PluginHeaderContribution.class.isInterface());
        assertTrue(PluginComposerContribution.class.isInterface());
        assertTrue(PluginChannelContext.class.isInterface());
        assertTrue(PluginComposerContext.class.isInterface());
    }

    @Test
    public void pluginEvolutionUsesJvmDefaultMethods() {
        assertTrue(method(AxycordPlugin.class, "getSettingsSchema").isDefault());
        assertTrue(method(AxycordPlugin.class, "getSettingsPage").isDefault());
        assertTrue(method(AxycordPlugin.class, "start", PluginScope.class).isDefault());
        assertTrue(method(AxycordPlugin.class, "stop").isDefault());
        assertFalse(Arrays.stream(AxycordPlugin.class.getMethods()).anyMatch(it -> it.getName().equals("getManifest")));
    }

    @Test
    public void implementationRecordsAreNotPublic() {
        assertNotPublic("mmaarij.axycord.plugins.api.PluginManifestValue");
        assertNotPublic("mmaarij.axycord.plugins.api.PluginSettingsSchemaValue");
        assertNotPublic("mmaarij.axycord.plugins.api.PluginHeaderContributionValue");
    }

    @Test
    public void factoryEntryPointsKeepTheirV1Descriptors() {
        method(
            AxycordPluginKt.class,
            "pluginManifest",
            String.class,
            String.class,
            String.class,
            int.class,
            Function1.class
        );
        method(PluginExtensionsKt.class, "pluginSettings", Function1.class);
        method(
            PluginExtensionsKt.class,
            "pluginHeaderContribution",
            String.class,
            String.class,
            Function1.class,
            Function1.class
        );
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void assertNotPublic(String name) {
        try {
            assertFalse(Modifier.isPublic(Class.forName(name).getModifiers()));
        } catch (ClassNotFoundException failure) {
            throw new AssertionError(failure);
        }
    }
}
