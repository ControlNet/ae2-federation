package space.controlnet.ae2federation.crafting.remote;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

/**
 * The native requester of the jobs a provider network runs for consumers, hosted on a Federation node of the
 * provider's Grid (a Bridge side or a Router face). AE2 hands it each job's final output, which it passes to the
 * consumer's Grid, where the consumer's waiting CPU takes it. Its links are saved with the node's host, so after a
 * reload AE2 reconnects the running jobs to it and nothing is requested twice.
 */
public final class RemoteCraftingRequester implements ICraftingRequester {
    private final String tagName;
    private final Supplier<@Nullable IGridNode> node;
    private final Runnable changed;
    private final List<Job> jobs = new ArrayList<>();
    private @Nullable ServerLevel registered;

    /** One native job on the provider for {@code key}'s consumer; {@code delivered} counts what reached it. */
    static final class Job {
        final ICraftingLink link;
        final PolicyKey key;
        final AEKey output;
        final long amount;
        long delivered;

        Job(ICraftingLink link, PolicyKey key, AEKey output, long amount, long delivered) {
            this.link = link;
            this.key = key;
            this.output = output;
            this.amount = amount;
            this.delivered = delivered;
        }

        long remaining() {
            return Math.max(0, amount - delivered);
        }

        boolean live() {
            return !link.isDone() && !link.isCanceled();
        }
    }

    /**
     * @param tagName the host's NBT key for these jobs, distinct per node of one host
     * @param changed marks the host for saving
     */
    public RemoteCraftingRequester(String tagName, Supplier<@Nullable IGridNode> node, Runnable changed) {
        this.tagName = tagName;
        this.node = node;
        this.changed = changed;
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        register();
        return jobs.stream().map(job -> job.link).collect(ImmutableSet.toImmutableSet());
    }

    /**
     * Passes the consumer what its job asked for. A pattern can make more than that (one log makes four planks), and
     * AE2 offers the whole output here; the surplus stays with the provider, in its own Grid's storage.
     */
    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        var job = job(link);
        var level = level();
        if (job == null || level == null) return 0;
        var source = IActionSource.ofMachine(this);
        long wanted = Math.min(amount, job.remaining());
        long delivered = wanted <= 0 ? 0
                : RemoteCraftingService.get(level).deliver(job.key.consumerNetworkId(), what, wanted, mode, source);
        long kept = 0;
        var current = node.get();
        if (delivered < amount && current != null && current.getGrid() != null) {
            kept = current.getGrid().getStorageService().getInventory().insert(what, amount - delivered, mode, source);
        }
        if (mode == Actionable.MODULATE && delivered > 0) {
            job.delivered += delivered;
            changed.run();
        }
        return delivered + kept;
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        if (jobs.removeIf(job -> job.link == link)) changed.run();
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return node.get();
    }

    void track(ICraftingLink link, PolicyKey key, AEKey output, long amount) {
        jobs.add(new Job(link, key, output, amount, 0));
        changed.run();
    }

    /** The jobs still running; finished or cancelled links are dropped first. */
    List<Job> liveJobs() {
        if (jobs.removeIf(job -> !job.live())) changed.run();
        return List.copyOf(jobs);
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var job : jobs) {
            if (!job.live()) continue;
            var entry = new CompoundTag();
            var link = new CompoundTag();
            job.link.writeToNBT(link);
            entry.put("link", link);
            entry.putUUID("consumer", job.key.consumerNetworkId().value());
            entry.putUUID("provider", job.key.providerNetworkId().value());
            entry.put("output", job.output.toTagGeneric(registries));
            entry.putLong("amount", job.amount);
            entry.putLong("delivered", job.delivered);
            list.add(entry);
        }
        if (list.isEmpty()) {
            data.remove(tagName);
        } else {
            data.put(tagName, list);
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        jobs.clear();
        for (var element : data.getList(tagName, Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            var output = AEKey.fromTagGeneric(registries, entry.getCompound("output"));
            if (output == null) continue;
            var key = new PolicyKey(new NetworkId(entry.getUUID("consumer")), new NetworkId(entry.getUUID("provider")),
                    PolicyCapability.CRAFTING);
            jobs.add(new Job(StorageHelper.loadCraftingLink(entry.getCompound("link"), this), key, output,
                    entry.getLong("amount"), entry.getLong("delivered")));
        }
    }

    private @Nullable Job job(ICraftingLink link) {
        for (var job : jobs) {
            if (job.link == link) return job;
        }
        return null;
    }

    /** AE2 asks for the requested jobs when the node joins its Grid, which is when the service learns of this one. */
    private void register() {
        var level = level();
        if (level != null && registered != level) {
            RemoteCraftingService.get(level).register(this);
            registered = level;
        }
    }

    @Nullable ServerLevel level() {
        var current = node.get();
        return current != null && current.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
    }
}
