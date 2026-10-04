package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with Applied Flux's FE storage. */
@PrefixGameTestTemplate(false)
public final class AppliedFluxCompatGameTests {
    private AppliedFluxCompatGameTests() {
    }

    /** The provider network's storage is a 1k FE Storage Cell holding energy as an ME resource. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void fluxCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "appflux:fe_1k_cell", fluxKey(), 100_000, 25_000);
        helper.succeedWhen(scene::tick);
    }

    /** Applied Flux's FE resource, from its own classes, since this test mod does not build against Applied Flux. */
    private static AEKey fluxKey() {
        try {
            var loader = AppliedFluxCompatGameTests.class.getClassLoader();
            var type = loader.loadClass("com.glodblock.github.appflux.common.me.key.type.EnergyType");
            Object fe = null;
            for (var constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("FE")) fe = constant;
            }
            return (AEKey) loader.loadClass("com.glodblock.github.appflux.common.me.key.FluxKey")
                    .getMethod("of", type).invoke(null, fe);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Applied Flux's FE resource is unavailable", exception);
        }
    }
}
