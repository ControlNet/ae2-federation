package space.controlnet.ae2federation.compat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How long Data Energistics' Trinity CPU takes for larger orders through one projected AE2 Pattern Provider, as
 * {@link DataEnergisticsCompatGameTests#trinityCpuSixteenRemoteRuns} orders sixteen runs. The report's job ticks of a
 * Federation run and a joined run compare the two (NativeParity). Opt-in: run with
 * {@code --tests data-energistics-benchmark}.
 */
@PrefixGameTestTemplate(false)
public final class DataEnergisticsBenchmarkGameTests {
    private DataEnergisticsBenchmarkGameTests() {
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "matter_fabrication_well", timeoutTicks = 3000)
    public static void trinityRemoteRuns64(GameTestHelper helper) {
        helper.succeedWhen(DataEnergisticsCompatGameTests.trinityRemoteRuns(helper, 64)::tick);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "matter_fabrication_well", timeoutTicks = 4000)
    public static void trinityRemoteRuns256(GameTestHelper helper) {
        helper.succeedWhen(DataEnergisticsCompatGameTests.trinityRemoteRuns(helper, 256)::tick);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "matter_fabrication_well", timeoutTicks = 6000)
    public static void trinityRemoteRuns1024(GameTestHelper helper) {
        helper.succeedWhen(DataEnergisticsCompatGameTests.trinityRemoteRuns(helper, 1024)::tick);
    }
}
