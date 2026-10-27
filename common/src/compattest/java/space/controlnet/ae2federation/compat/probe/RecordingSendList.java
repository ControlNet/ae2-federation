package space.controlnet.ae2federation.compat.probe;

import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Development-only: a pattern provider's send list that tells {@link DispatchProbe} whenever it grows. Addons such as
 * Data Energistics fill the list through an accessor rather than through {@code addToSendList}, so the list itself is
 * the only place every write passes. It replaces AE2's {@code ArrayList} only in runs with the dispatch probe on.
 */
public final class RecordingSendList extends ArrayList<GenericStack> {
    private final Object logic;

    public RecordingSendList(Object logic, Collection<GenericStack> initial) {
        super(initial);
        this.logic = logic;
    }

    @Override
    public boolean add(GenericStack stack) {
        DispatchProbe.sendListGrew(logic, 1);
        return super.add(stack);
    }

    @Override
    public void add(int index, GenericStack stack) {
        DispatchProbe.sendListGrew(logic, 1);
        super.add(index, stack);
    }

    @Override
    public boolean addAll(Collection<? extends GenericStack> stacks) {
        DispatchProbe.sendListGrew(logic, stacks.size());
        return super.addAll(stacks);
    }

    @Override
    public boolean addAll(int index, Collection<? extends GenericStack> stacks) {
        DispatchProbe.sendListGrew(logic, stacks.size());
        return super.addAll(index, stacks);
    }
}
