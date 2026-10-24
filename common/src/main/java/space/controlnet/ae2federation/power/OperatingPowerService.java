package space.controlnet.ae2federation.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainRegistry;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.DeviceStructureUpdater;

/**
 * Runs each Federation domain on its Beam Converters, once per server tick before the services that link networks read
 * the registry. A domain uses the cost of its blocks ({@link OperatingPowerCosts}) and draws it from the pooled buffers
 * of its converters whose front is the domain's largest cable cross-section; a converter of another size supplies
 * nothing. A domain that cannot pay stops as a whole ({@link FederationDomainRegistry#setUnpowered}) and runs again as
 * an AE2 grid does ({@link PowerLedger}). With the requirement off, no domain stops and nothing is drawn.
 *
 * <p>What each domain uses, which converters it has and whether they match are planned again only when the domain
 * evidence or the rule changes; each tick then reads the converters' buffers and input. Server thread only.
 */
public final class OperatingPowerService {
    private static final Map<MinecraftServer, OperatingPowerService> SERVICES = new WeakHashMap<>();
    /** How many ticks of input and use the gauge weighs before it calls a domain draining: five seconds. */
    static final int FLOW_WINDOW_TICKS = 100;

    private final Map<FederationDomainId, PowerLedger> ledgers = new HashMap<>();
    private final Map<FederationDomainId, Reading> readings = new HashMap<>();
    /** What each domain took in and how many ticks it settled since the statistics last drained them. */
    private final Map<FederationDomainId, Gathered> gathered = new HashMap<>();
    private final Map<FederationDomainId, Flow> flows = new HashMap<>();
    private Map<FederationDomainId, Plan> plans = Map.of();
    private FederationDomainRegistry plannedRegistry;
    private long plannedRevision = -1;
    private double plannedMultiplier = Double.NaN;

    private OperatingPowerService() {
    }

    public static synchronized void tick(MinecraftServer server) {
        SERVICES.computeIfAbsent(server, ignored -> new OperatingPowerService()).run(server);
    }

    /** The last tick's power of a domain, or empty when power is not required or the domain is new. */
    public static synchronized Optional<Reading> reading(MinecraftServer server, FederationDomainId federationDomainId) {
        var service = SERVICES.get(server);
        return service == null ? Optional.empty() : Optional.ofNullable(service.readings.get(federationDomainId));
    }

    /**
     * Each domain's power since the last call, for the statistics page, which records once a second: its input and its
     * bodies' input averaged over the ticks it settled, and the rest as of the last tick. Empty while power is not
     * required. Resets what it returns.
     */
    public static synchronized Map<FederationDomainId, Second> drainSecond(MinecraftServer server) {
        var service = SERVICES.get(server);
        if (service == null) return Map.of();
        var out = new HashMap<FederationDomainId, Second>();
        service.gathered.forEach((id, gathered) -> {
            var reading = service.readings.get(id);
            if (reading == null || gathered.ticks == 0) return;
            var bodies = new ArrayList<ConverterBody>();
            gathered.bodies.forEach((site, input) -> bodies.add(new ConverterBody(site.dimension(), site.origin(),
                    site.size(), input / gathered.ticks, site.matches())));
            out.put(id, new Second(reading.use(), gathered.input / gathered.ticks, reading.stored(), reading.capacity(),
                    reading.running(), List.copyOf(bodies)));
        });
        service.gathered.clear();
        return out;
    }

    public static synchronized void closeServer(MinecraftServer server) {
        SERVICES.remove(server);
    }

    private void run(MinecraftServer server) {
        var registry = FederationDomainRegistryAccess.get(server.overworld());
        if (!OperatingPowerRule.required()) {
            registry.unpoweredDomains().forEach(id -> registry.setUnpowered(id, false));
            ledgers.clear();
            readings.clear();
            gathered.clear();
            flows.clear();
            plans = Map.of();
            plannedRevision = -1;
            return;
        }
        var domains = registry.federationDomains();
        if (registry != plannedRegistry || registry.evidenceRevision() != plannedRevision
                || OperatingPowerRule.multiplier() != plannedMultiplier) {
            plans = plan(server, registry, domains.stream().map(domain -> domain.federationDomainId()).toList());
            plannedRegistry = registry;
            plannedRevision = registry.evidenceRevision();
            plannedMultiplier = OperatingPowerRule.multiplier();
        }
        var live = new HashSet<FederationDomainId>();
        for (var domain : domains) {
            var id = domain.federationDomainId();
            var plan = plans.get(id);
            if (plan == null) continue;
            live.add(id);
            settle(registry, id, plan);
        }
        ledgers.keySet().retainAll(live);
        readings.keySet().retainAll(live);
        gathered.keySet().retainAll(live);
        flows.keySet().retainAll(live);
    }

    private void settle(FederationDomainRegistry registry, FederationDomainId id, Plan plan) {
        var matching = new ArrayList<BeamConverterBlockEntity>();
        var matchingBodies = new ArrayList<BodySite>();
        var converters = new ArrayList<ConverterReading>();
        var input = 0.0;
        var creative = false;
        var gathered = this.gathered.computeIfAbsent(id, ignored -> new Gathered());
        for (var site : plan.converters()) {
            if (!site.level().isLoaded(site.position())
                    || !(site.level().getBlockEntity(site.position()) instanceof BeamConverterBlockEntity converter)) {
                continue;
            }
            var taken = converter.takeInput();
            converters.add(new ConverterReading(site.level().dimension().location().toString(), site.position(), taken,
                    site.matches()));
            gathered.bodies.merge(site.body(), taken, Double::sum);
            if (!site.matches()) continue;
            matching.add(converter);
            matchingBodies.add(site.body());
            input += taken;
            creative |= converter.creative();
        }
        // A creative converter supplies whatever the domain uses.
        if (creative) input = Math.max(input, plan.use().total());
        var stored = new double[matching.size()];
        for (var index = 0; index < stored.length; index++) stored[index] = matching.get(index).stored();
        var ledger = ledgers.computeIfAbsent(id, ignored -> new PowerLedger());
        // A creative converter never runs out: the domain pays its use without drawing on the others.
        var running = creative ? ledger.settle(new double[] {plan.use().total()}, plan.use().total())
                : ledger.settle(stored, plan.use().total());
        gathered.input += input;
        gathered.ticks++;
        // A body is one buffer: its blocks keep equal shares of what it holds.
        var shares = new HashMap<BodySite, double[]>();
        for (var index = 0; index < stored.length; index++) {
            var share = shares.computeIfAbsent(matchingBodies.get(index), ignored -> new double[2]);
            share[0] += stored[index];
            share[1]++;
        }
        for (var index = 0; index < stored.length; index++) {
            var share = shares.get(matchingBodies.get(index));
            stored[index] = share[0] / share[1];
        }
        var left = 0.0;
        for (var index = 0; index < stored.length; index++) {
            if (!creative) matching.get(index).setStored(stored[index]);
            left += matching.get(index).stored();
        }
        registry.setUnpowered(id, !running);
        // A converter takes whole FE and a source may push in bursts, so one tick's input says little: the domain is
        // draining once its buffers lost more over the window than that rounding (1 FE a block) explains.
        var flow = flows.computeIfAbsent(id, ignored -> new Flow());
        var draining = running && flow.add(input - plan.use().total()) < -Math.max(1, matching.size());
        readings.put(id, new Reading(plan.use(), input, left, matching.size() * BeamConverterBlockEntity.CAPACITY,
                running, draining, plan.section(), List.copyOf(converters)));
    }

    private static Map<FederationDomainId, Plan> plan(MinecraftServer server, FederationDomainRegistry registry,
            List<FederationDomainId> domains) {
        var levels = new HashMap<String, ServerLevel>();
        server.getAllLevels().forEach(level -> levels.put(FederationDomainRegistryAccess.dimension(level), level));
        var plans = new HashMap<FederationDomainId, Plan>();
        for (var id : domains) {
            var evidence = registry.nodeEvidence(id);
            // A linked Beam Former pair or Quantum Cube pair counts as cable of its square; its anchor names the link.
            var section = CableCrossSection.largest(evidence.stream()
                    .filter(node -> node.kind() == FederationDomainNodeKind.CABLE
                            || OperatingPowerCosts.beam(node).isPresent() || OperatingPowerCosts.quantumLinked(node))
                    .map(FederationDomainNodeEvidence::capacityFactor).toList())
                    .orElse(CableCrossSection.ONE_BY_ONE);
            var bodies = new ArrayList<Body>();
            var converters = new ArrayList<ConverterSite>();
            for (var node : evidence) {
                var level = levels.get(node.nodeId().dimension());
                if (node.kind() != FederationDomainNodeKind.CONVERTER || level == null) continue;
                var position = BlockPos.of(node.nodeId().blockPosition());
                var body = bodies.stream().filter(known -> known.contains(level, position)).findFirst()
                        .orElseGet(() -> {
                            var judged = Body.of(level, position);
                            bodies.add(judged);
                            return judged;
                        });
                var matches = body.section().filter(section::equals).isPresent();
                converters.add(new ConverterSite(level, position, matches, body.site(node.nodeId().dimension(), position,
                        matches)));
            }
            plans.put(id, new Plan(OperatingPowerCosts.use(evidence), section, List.copyOf(converters)));
        }
        return plans;
    }

    /** What a domain uses, the cross-section its converters must have, and where they are. */
    private record Plan(OperatingPowerCosts.Use use, CableCrossSection section, List<ConverterSite> converters) {
    }

    private record ConverterSite(ServerLevel level, BlockPos position, boolean matches, BodySite body) {
    }

    /** A converter body as the statistics page lists it: where it starts, its size, and whether it matches. */
    private record BodySite(String dimension, BlockPos origin, String size, boolean matches) {
    }

    /** What one domain took in, in total and by body, over the ticks it settled since the last drain. */
    private static final class Gathered {
        private final Map<BodySite, Double> bodies = new java.util.LinkedHashMap<>();
        private double input;
        private int ticks;
    }

    /** A domain's input less its use over the last {@link #FLOW_WINDOW_TICKS} ticks. */
    private static final class Flow {
        private final double[] net = new double[FLOW_WINDOW_TICKS];
        private int next;

        /** Records a tick's input less use and returns the window's total. */
        double add(double tick) {
            net[next] = tick;
            next = (next + 1) % net.length;
            var total = 0.0;
            for (var value : net) total += value;
            return total;
        }
    }

    /** A converter body as judged when planning, and the cross-section of its front if it is one. */
    private record Body(ServerLevel level, DeviceStructure.Result result, Optional<CableCrossSection> section) {
        static Body of(ServerLevel level, BlockPos position) {
            var state = level.getBlockState(position);
            if (!(state.getBlock() instanceof BeamConverterBlock)) {
                return new Body(level, DeviceStructure.Unknown.INSTANCE, Optional.empty());
            }
            var result = DeviceStructureUpdater.body(level, position);
            var section = result instanceof DeviceStructure.Valid valid
                    ? BeamConverterShape.of(valid.min(), valid.max()) : Optional.<CableCrossSection>empty();
            return new Body(level, result, section);
        }

        /**
         * The body as listed: from its lowest corner, sized across its front then along it (a converter's own shape)
         * or by its extents along x, y and z when it is not one. An unformed block is listed on its own.
         */
        BodySite site(String dimension, BlockPos position, boolean matches) {
            if (!(result instanceof DeviceStructure.Valid valid)) return new BodySite(dimension, position, "1x1x1", matches);
            var size = valid.max().subtract(valid.min()).offset(1, 1, 1);
            var label = section.map(cross -> cross.narrow() + "x" + cross.wide() + "x" + cross.converterLength())
                    .orElse(size.getX() + "x" + size.getY() + "x" + size.getZ());
            return new BodySite(dimension, valid.min(), label, matches);
        }

        boolean contains(ServerLevel other, BlockPos position) {
            return other == level && result instanceof DeviceStructure.Valid valid && valid.contains(position);
        }
    }

    /**
     * One tick of a domain's power: its use, the matching converters' input, what their buffers hold after the draw
     * and can hold, whether it runs, whether it is draining (its buffers lost FE over the last five seconds), the
     * cross-section its converters must have, and each converter block's input.
     */
    public record Reading(OperatingPowerCosts.Use use, double input, double stored, double capacity, boolean running,
            boolean draining, CableCrossSection section, List<ConverterReading> converters) {
        /**
         * The topology's corner gauge, "percent:state": the buffers' fill rounded to a whole percent, and stopped,
         * draining or running.
         */
        public String gauge() {
            int percent = capacity <= 0 ? 0 : (int) Math.round(100 * stored / capacity);
            return percent + ":" + (!running ? "stopped" : draining ? "draining" : "running");
        }
    }

    /**
     * One second of a domain's power: its use, its matching converters' input per tick averaged over the second, what
     * their buffers hold and can hold, whether it runs, and each converter body's average input.
     */
    public record Second(OperatingPowerCosts.Use use, double input, double stored, double capacity, boolean running,
            List<ConverterBody> bodies) {
    }

    /** A converter body, from its lowest corner: its size, its input per tick and whether it matches its domain. */
    public record ConverterBody(String dimension, BlockPos origin, String size, double input, boolean matches) {
    }

    /** One converter block's input this tick, and whether its body matches the domain's cross-section. */
    public record ConverterReading(String dimension, BlockPos position, double input, boolean matches) {
    }
}
