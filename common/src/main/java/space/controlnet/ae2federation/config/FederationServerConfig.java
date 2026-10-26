package space.controlnet.ae2federation.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import space.controlnet.ae2federation.beam.BeamLinkRule;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.power.OperatingPowerRule;

/**
 * The gameplay rules a server sets for its world, in {@code config/ae2federation-server.toml}; a world's
 * {@code serverconfig} folder may override it. They are on by default; a pack author may turn them off. Editing the
 * file while the server runs takes effect at once.
 */
public final class FederationServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENFORCE_CABLE_CAPACITY;
    private static final ModConfigSpec.IntValue CABLE_CAPACITY;
    private static final ModConfigSpec.BooleanValue REQUIRE_OPERATING_POWER;
    private static final ModConfigSpec.DoubleValue OPERATING_COST_MULTIPLIER;
    private static final ModConfigSpec.IntValue BEAM_RANGE;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("capacity");
        ENFORCE_CABLE_CAPACITY = builder.comment(
                        "Limit how many networks and devices one Federation Cable carries. When any cable of a",
                        "Federation domain carries more than cableCapacity, its Federation domain stops: its networks no",
                        "longer share storage, crafting, processing or energy until the count fits again. The count",
                        "itself is shown either way.")
                .define("enforceCableCapacity", true);
        CABLE_CAPACITY = builder.comment(
                        "Units one Federation Cable carries: each ME network on a Switch, each Processing Endpoint and",
                        "each Federation Pattern Provider counts 1. Used only while enforceCableCapacity is true.")
                .defineInRange("cableCapacity", CableCapacityLimit.DEFAULT_CAPACITY, 1, CableCapacityLimit.MAX_CAPACITY);
        builder.pop();
        builder.push("power");
        REQUIRE_OPERATING_POWER = builder.comment(
                        "Make Federation domains run on FE fed into an ME Federation Beam Converter. Every Federation",
                        "block costs FE each tick; when a domain's converters cannot pay, the domain stops: its networks",
                        "no longer share storage, crafting, processing or energy until power returns. When false, domains",
                        "run on unlimited energy.")
                .define("requireOperatingPower", true);
        OPERATING_COST_MULTIPLIER = builder.comment(
                        "Multiplies every Federation block's FE cost per tick. Used only while requireOperatingPower is",
                        "true.")
                .defineInRange("operatingCostMultiplier", OperatingPowerRule.DEFAULT_MULTIPLIER, 0.0,
                        OperatingPowerRule.MAX_MULTIPLIER);
        builder.pop();
        builder.push("beam");
        BEAM_RANGE = builder.comment(
                        "How many blocks may lie between the fronts of two ME Federation Beam Formers that link. Longer",
                        "beams cost more operating power and notice a block put in their way later.")
                .defineInRange("maxRange", BeamLinkRule.DEFAULT_RANGE, 1, BeamLinkRule.MAX_RANGE);
        builder.pop();
        SPEC = builder.build();
    }

    private FederationServerConfig() {
    }

    public static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
        modBus.addListener(ModConfigEvent.Loading.class, event -> apply(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> apply(event.getConfig()));
        modBus.addListener(ModConfigEvent.Unloading.class, event -> {
            // The next world starts from the defaults until its own file loads.
            if (event.getConfig().getSpec() != SPEC) return;
            CableCapacityLimit.set(true, CableCapacityLimit.DEFAULT_CAPACITY);
            OperatingPowerRule.set(true, OperatingPowerRule.DEFAULT_MULTIPLIER);
            BeamLinkRule.set(BeamLinkRule.DEFAULT_RANGE);
        });
    }

    private static void apply(ModConfig config) {
        if (config.getSpec() != SPEC) return;
        CableCapacityLimit.set(ENFORCE_CABLE_CAPACITY.get(), CABLE_CAPACITY.get());
        OperatingPowerRule.set(REQUIRE_OPERATING_POWER.get(), OPERATING_COST_MULTIPLIER.get());
        BeamLinkRule.set(BEAM_RANGE.get());
    }
}
