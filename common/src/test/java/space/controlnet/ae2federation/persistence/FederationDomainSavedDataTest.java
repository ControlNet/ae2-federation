package space.controlnet.ae2federation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainRecords;

final class FederationDomainSavedDataTest {
    @Test
    void recordsAndTheSequenceSurviveASave() {
        var records = new FederationDomainRecords();
        var router = new FederationDomainNodeId("minecraft:overworld", 7);
        var tunnel = new FederationDomainNodeId("minecraft:the_nether", -3, "p2p:north");
        var cable = new FederationDomainNodeId("minecraft:overworld", 9);
        records.advanceSequence(5);
        records.put(2, Set.of(router, tunnel), Set.of());
        records.put(5, Set.of(), Set.of(cable));

        var loaded = FederationDomainSavedData.load(new FederationDomainSavedData(records).save(new CompoundTag(), null),
                null).records();

        assertEquals(5, loaded.sequence());
        assertEquals(records.all(), loaded.all());
        assertEquals(java.util.OptionalLong.of(2), loaded.owner(tunnel));
    }

    @Test
    void aChangeMarksTheDataForSaving() {
        var data = new FederationDomainSavedData(new FederationDomainRecords());
        assertTrue(!data.isDirty());
        data.records().advanceSequence(1);
        assertTrue(data.isDirty());
    }
}
