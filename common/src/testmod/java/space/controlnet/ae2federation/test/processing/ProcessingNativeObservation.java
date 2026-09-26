package space.controlnet.ae2federation.test.processing;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.test.mixed.MixedFactoryRuntimeReceipt;

public final class ProcessingNativeObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingNativeObservation.class);
    private static final List<String> EVENTS = new ArrayList<>();
    private static long sequence;

    private ProcessingNativeObservation() {
    }

    public static synchronized void reset() {
        EVENTS.clear();
        sequence = 0;
    }

    public static synchronized void recordPush(Object owner, KeyCounter[] inputs) {
        long amount = 0;
        int keys = 0;
        for (var counter : inputs) {
            for (var input : counter) {
                amount += input.getLongValue();
                keys++;
            }
        }
        record("push-head", owner, "keys=" + keys + ",amount=" + amount);
    }

    public static synchronized void recordPushResult(Object owner, boolean accepted) {
        record("push-return", owner, "accepted=" + accepted);
    }

    public static synchronized void recordRemainder(Object owner, AEKey key, long amount) {
        record("send-remainder", owner, "key=" + key.getType().getId() + ",amount=" + amount);
    }

    public static synchronized void recordReturn(Object owner, boolean changed) {
        record("return-inject", owner, "changed=" + changed);
    }

    public static synchronized void recordLifecycle(String operation, Object owner) {
        record(operation, owner, "native=true");
    }

    public static synchronized void recordFact(String operation, Object owner, String detail) {
        record(operation, owner, detail);
    }

    public static synchronized void recordLogicState(String operation, PatternProviderLogic logic,
            List<ItemStack> drops) {
        var access = (space.controlnet.ae2federation.test.mixin.PatternProviderLogicReturnAccess) logic;
        var owner = identity(logic);
        var sendOwner = identity(access.ae2federation_test$getSendList());
        var returnOwner = identity(logic.getReturnInv());
        var patterns = itemStacks(logic.getPatternInv());
        var send = genericStacks(access.ae2federation_test$getSendList());
        var returns = returnInventory(logic);
        var dropped = itemStacks(drops);
        var lock = logic.getCraftingLockedReason().toString();
        var unlock = genericStack(logic.getUnlockStack());
        LOGGER.info("AE2F_PROCESSING_NATIVE_STATE testId={} operation={} owner={} sendOwner={} returnOwner={}"
                        + " patterns={} send={} returns={} drops={} lock={} unlock={}",
                System.getProperty("ae2federation.testId", "unknown"), operation, identity(logic),
                sendOwner, returnOwner, patterns, send, returns, dropped, lock, unlock);
        receipt("state", operation, owner, "sendOwner=" + sendOwner + ";returnOwner=" + returnOwner
                + ";patterns=" + patterns + ";send=" + send + ";returns=" + returns + ";drops=" + dropped
                + ";lock=" + lock + ";unlock=" + unlock);
    }

    public static synchronized void recordTarget(String operation, Object owner, String snapshot) {
        var ownerIdentity = identity(owner);
        LOGGER.info("AE2F_PROCESSING_NATIVE_TARGET testId={} operation={} owner={} snapshot={}",
                System.getProperty("ae2federation.testId", "unknown"), operation, ownerIdentity, snapshot);
        receipt("target", operation, ownerIdentity, "snapshot=" + snapshot);
    }

    public static synchronized long count(String operation) {
        return EVENTS.stream().filter(event -> event.startsWith(operation + "|")).count();
    }

    public static synchronized long count(String operation, String detail) {
        return EVENTS.stream().filter(event -> event.startsWith(operation + "|") && event.endsWith(detail)).count();
    }

    public static synchronized List<Receipt> snapshot() {
        return EVENTS.stream().map(event -> {
            var fields = event.split("\\|", 3);
            return new Receipt(fields[0], fields[1], fields[2]);
        }).toList();
    }

    public record Receipt(String operation, String owner, String detail) {
    }

    public static synchronized String owners(String operation) {
        return EVENTS.stream().filter(event -> event.startsWith(operation + "|"))
                .map(event -> event.split("\\|", 3)[1]).distinct().sorted().reduce((left, right) -> left + "," + right)
                .orElse("0");
    }

    public static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    public static String genericStacks(List<GenericStack> stacks) {
        return stacks.stream().map(ProcessingNativeObservation::genericStack).sorted()
                .reduce((left, right) -> left + "," + right).orElse("empty");
    }

    public static String returnInventory(PatternProviderLogic logic) {
        var values = new ArrayList<GenericStack>();
        var inventory = logic.getReturnInv();
        for (int slot = 0; slot < inventory.size(); slot++) {
            var key = inventory.getKey(slot);
            if (key != null) {
                values.add(new GenericStack(key, inventory.getAmount(slot)));
            }
        }
        return genericStacks(values);
    }

    public static String itemStacks(Iterable<ItemStack> stacks) {
        var amounts = new TreeMap<String, Integer>();
        for (var stack : stacks) {
            if (!stack.isEmpty()) {
                amounts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        return amounts.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + "," + right).orElse("empty");
    }

    private static String genericStack(GenericStack stack) {
        if (stack == null) {
            return "empty";
        }
        var key = stack.what();
        var id = key instanceof AEItemKey itemKey
                ? BuiltInRegistries.ITEM.getKey(itemKey.getItem()).toString()
                : key instanceof AEFluidKey fluidKey
                        ? BuiltInRegistries.FLUID.getKey(fluidKey.getFluid()).toString()
                        : key.getType().getId().toString();
        return id + ":" + stack.amount();
    }

    private static void record(String operation, Object owner, String detail) {
        var ownerIdentity = identity(owner);
        EVENTS.add(operation + "|" + ownerIdentity + "|" + detail);
        MixedFactoryRuntimeReceipt.processing(operation, owner, detail);
        LOGGER.info("AE2F_PROCESSING_NATIVE_OBSERVATION testId={} operation={} owner={} detail={}",
                System.getProperty("ae2federation.testId", "unknown"), operation, ownerIdentity, detail);
        receipt("observation", operation, ownerIdentity, "detail=" + detail);
    }

    private static void receipt(String kind, String operation, String owner, String fields) {
        sequence++;
        LOGGER.info("AE2F_PROCESSING_RECEIPT testId={} sequence={} kind={} operation={} owner={} fields={}",
                System.getProperty("ae2federation.testId", "unknown"), sequence, kind, operation, owner, fields);
    }
}
