package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.cobblestone;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.planks;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.sticks;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEItemKey;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.observability.statistics.StatisticsBook;
import space.controlnet.ae2federation.observability.statistics.StatisticsNames;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;
import space.controlnet.ae2federation.persistence.StatisticsSavedData;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * The statistics page's numbers come from real operations: what one network takes out of another's storage, the
 * inputs a CPU pushes to another network's provider and the outputs that come back, pushes, started and finished
 * jobs, and each network's own stock, by resource type and by item, which a storage rule must not inflate.
 */
@PrefixGameTestTemplate(false)
public final class StatisticsGameTests {
    private static final String ITEMS = AEKeyType.items().getId().toString();

    private StatisticsGameTests() {
    }

    /**
     * The consumer takes three cobblestone out of the provider's chest through its storage rule, then crafts four
     * sticks with the provider's Molecular Assembler: two planks go to the provider, four sticks come back.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1200)
    public static void statisticsCountRealTraffic(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var statistics = LevelObservabilityService.get(helper.getLevel()).statistics();
        var stage = new int[1];
        var lastSecond = new long[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            var consumer = fixture.consumerNetwork();
            var provider = fixture.providerNetwork();
            if (stage[0] == 0) {
                fixture.putInProvider(cobblestone(), 5);
                fixture.putInConsumer(planks(), 2);
                fixture.enableRules();
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                var network = fixture.consumerGrid().getStorageService().getInventory();
                helper.assertValueEqual(network.extract(cobblestone(), 3, Actionable.SIMULATE, IActionSource.empty()), 3L,
                        "Waiting for the provider's storage on the consumer");
                helper.assertTrue(fixture.consumerService().isCraftable(sticks()), "Waiting for the projected patterns");
                helper.assertValueEqual(network.extract(cobblestone(), 3, Actionable.MODULATE, IActionSource.empty()), 3L,
                        "The consumer takes three cobblestone from the provider");
                fixture.begin(sticks(), 4);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                // Member networks are looked up once a wall-clock second, and a batch can run this whole test within
                // one: look them up now, or a job that starts and ends before the first lookup is not seen.
                statistics.finishSecond(statistics.nowSecond() - 1);
                helper.assertTrue(!statistics.cpus(consumer).isEmpty(), "Waiting for the statistics to see the consumer");
                helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the consumer's job to finish");
                helper.assertValueEqual(fixture.owed(sticks()), 0L, "Waiting for every stick to come back");
                lastSecond[0] = statistics.nowSecond();
                stage[0] = 4;
            }
            // The service writes a second once the wall clock passes it, and a GameTest server runs its ticks faster
            // than that: each attempt writes the next second itself. Ten of them read each network's stock once;
            // the job's end is counted at the crafting service's next ledger sweep.
            statistics.finishSecond(++lastSecond[0]);
            var book = statistics.book();
            long now = lastSecond[0];
            var range = StatisticsRange.MINUTES;
            helper.assertValueEqual(moved(book, provider, consumer, cobblestone(), now), 3d,
                    "Three cobblestone moved from the provider to the consumer");
            helper.assertValueEqual(moved(book, consumer, provider, planks(), now), 2d,
                    "The push sent the two planks to the provider");
            helper.assertValueEqual(moved(book, provider, consumer, sticks(), now), 4d,
                    "Four sticks came back to the consumer");
            // Sticks the finished job stored through the consumer's storage rule into the provider's chest moved
            // back to the provider: a second, real hop.
            double sticksStoredOnProvider = fixture.held(fixture.providerChest(), sticks());
            helper.assertValueEqual(moved(book, consumer, provider, sticks(), now), sticksStoredOnProvider,
                    "Sticks stored in the provider's chest are counted as moved there");
            helper.assertValueEqual(book.total(StatisticsNames.moved(provider, consumer, ITEMS), range, now),
                    7d, "Items moved to the consumer: 3 cobblestone and 4 sticks");
            helper.assertValueEqual(book.total(StatisticsNames.pair(StatisticsNames.PUSHED, consumer, provider), range,
                    now), 1d, "One pattern push");
            helper.assertValueEqual(book.total(StatisticsNames.network(StatisticsNames.DONE, consumer), range, now), 1d,
                    "One finished job");
            helper.assertValueEqual(book.total(StatisticsNames.network(StatisticsNames.STARTED, consumer), range, now), 1d,
                    "The consumer's CPU started one job");
            // Each network's own stock, read every ten seconds from its native storage only.
            double consumerOwn = fixture.held(fixture.consumerChest(), sticks()) + fixture.held(fixture.consumerChest(), planks());
            double providerOwn = fixture.held(fixture.providerChest(), cobblestone()) + sticksStoredOnProvider;
            helper.assertValueEqual(book.latest(StatisticsNames.stored(consumer, ITEMS), range, now), consumerOwn,
                    "The consumer's stock is its own chest, not what the storage rule shows of the provider");
            helper.assertValueEqual(book.latest(StatisticsNames.stored(provider, ITEMS), range, now), providerOwn,
                    "The provider's stock is its own chest");
            var providerStock = StatisticsNames.network(StatisticsNames.STOCK, provider);
            helper.assertValueEqual(latest(book.keyGauge(providerStock, cobblestone(), range, now)),
                    (double) fixture.held(fixture.providerChest(), cobblestone()), "The provider's cobblestone is kept by item");
            helper.assertTrue(!book.keys(StatisticsNames.network(StatisticsNames.STOCK, consumer)).contains(cobblestone()),
                    "The consumer holds no cobblestone of its own, whatever its storage rule shows");
            PolicyEvidence.write("statisticscountrealtraffic", 10, Map.of("cobblestoneToConsumer", "3",
                    "planksToProvider", "2", "sticksToConsumer", "4", "pushes", "1", "jobsDone", "1"));
            fixture.close();
        });
    }

    /** The last day of statistics survives a save: timelines, the observed seconds and the "most moved" keys. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true)
    public static void statisticsSurviveASave(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var book = new StatisticsBook<AEKey>();
        var from = new NetworkId(java.util.UUID.randomUUID());
        var to = new NetworkId(java.util.UUID.randomUUID());
        long start = 1_000_000;
        for (long second = start; second < start + 700; second++) {
            book.observe(second);
            book.gauge(StatisticsNames.network(StatisticsNames.POWER, from), second, second % 100);
        }
        book.count(StatisticsNames.moved(from, to, ITEMS), start + 650, 64);
        book.moved(StatisticsNames.pair(StatisticsNames.MOVED, from, to), start + 650, AEItemKey.of(Items.IRON_INGOT), 64);
        book.moved(StatisticsNames.pair(StatisticsNames.MOVED, from, to), start + 651, AEItemKey.of(Items.GOLD_INGOT), 8);
        var stock = StatisticsNames.network(StatisticsNames.STOCK, from);
        for (long second = start + 600; second < start + 700; second++) {
            book.keyGauge(stock, AEItemKey.of(Items.IRON_INGOT), second, second % 50);
        }
        book.pin(stock, java.util.Set.of(AEItemKey.of(Items.DIAMOND), AEItemKey.of(Items.GOLD_INGOT)));
        var data = new StatisticsSavedData(book);
        data.changed(start + 699);
        var tag = data.save(new CompoundTag(), registries);
        var loaded = StatisticsSavedData.load(tag, registries).book();
        long now = start + 699;
        for (var range : StatisticsRange.values()) {
            helper.assertTrue(java.util.Arrays.equals(book.observed(range, now), loaded.observed(range, now)),
                    "Observed seconds survive: " + range);
            helper.assertTrue(java.util.Arrays.equals(
                    book.gauge(StatisticsNames.network(StatisticsNames.POWER, from), range, now),
                    loaded.gauge(StatisticsNames.network(StatisticsNames.POWER, from), range, now)), "Gauge survives: " + range);
            helper.assertTrue(java.util.Arrays.equals(book.rate(StatisticsNames.moved(from, to, ITEMS), range, now),
                    loaded.rate(StatisticsNames.moved(from, to, ITEMS), range, now)), "Counter survives: " + range);
            helper.assertValueEqual(loaded.ranking(StatisticsNames.pair(StatisticsNames.MOVED, from, to), range, now, 4),
                    book.ranking(StatisticsNames.pair(StatisticsNames.MOVED, from, to), range, now, 4),
                    "Ranking survives: " + range);
            helper.assertTrue(java.util.Arrays.equals(book.keyGauge(stock, AEItemKey.of(Items.IRON_INGOT), range, now),
                    loaded.keyGauge(stock, AEItemKey.of(Items.IRON_INGOT), range, now)), "Stock by item survives: " + range);
        }
        helper.assertValueEqual(loaded.pins(stock), book.pins(stock), "Pins survive");
        PolicyEvidence.write("statisticssurviveasave", 16, Map.of("ranges", "4", "pins", "2"));
        helper.succeed();
    }

    private static double latest(double[] points) {
        for (int index = points.length - 1; index >= 0; index--) if (!Double.isNaN(points[index])) return points[index];
        return Double.NaN;
    }

    private static double moved(StatisticsBook<AEKey> book, NetworkId from, NetworkId to, AEKey key, long now) {
        return book.ranking(StatisticsNames.pair(StatisticsNames.MOVED, from, to), StatisticsRange.MINUTES, now, 64)
                .stream().filter(ranked -> ranked.key().equals(key)).mapToDouble(ranked -> ranked.amount()).sum();
    }
}
