# Native Energy Directionality Gate

## Status

Task 10 is `BLOCKED` on Minecraft 1.21.1, NeoForge 21.1.250, AE2 19.2.17, and Java 21.
The five serialized GameTests establish the available native behavior, but the pinned Quartz Fiber-style overlay cannot
enforce a caller-specific supply direction.

The exact missing hook is: shared `EnergyOverlayGrid` has no caller/edge direction, route identity, or authorization
boundary. `IEnergyOverlayGridConnection.connectedEnergyServices()` returns services only. After the declaring consumer
builds the overlay, AE2 assigns the same overlay instance to every discovered `EnergyService`, so provider-side extraction
can reach consumer storage.

## Qualified Native Behavior

- Two distinct native `IGrid` objects share power without joining data Grids.
- `IEnergyService.extractAEPower` debits a real finite `EnergyCellBlockEntity` through `IAEPowerStorage`.
- The directional case records source debit only. AE2 satisfies an extraction call and performs no separate target receipt.
- An initially loaded unpowered consumer becomes powered after overlay discovery and AE2's 30-tick stabilization.
- With no charged source, extraction is zero and the consumer remains unpowered.
- A three-service ring terminates by identity deduplication and loses only the energy explicitly extracted.
- Boundary nodes use `GridFlags.CANNOT_CARRY`, zero idle power, and no FE storage or synthetic energy key.

## Decisive Blocker

The reverse case declares only `consumer -> provider`, forces discovery from the consumer, then extracts from the
provider. AE2 returns 200 AE and debits the consumer cell by 200 AE. This is native overlay symmetry, not rejection.
Ring deduplication prevents recursion and energy creation but supplies no directional authorization.

No production energy boundary was added. Closing this gate requires a native binding that receives caller or route
identity at extraction time, or an equivalent authorization boundary before shared-overlay provider traversal.

## Evidence

The canonical command intentionally exits nonzero after writing a schema-v3 `BLOCKED` report:

```sh
./gradlew :neoforge-1.21.1:federationVerify -Pcases=energy-proof.directional,energy-proof.cold-start,energy-proof.reject-reverse,energy-proof.no-source,energy-proof.ring -PevidenceDir=.omo/evidence/task-10 --no-configuration-cache
```

Expected signal: `Task 10 BLOCKED: shared EnergyOverlayGrid has no caller/edge direction, route identity, or authorization boundary`.

Ordinary persisted consumption rejects this result as incomplete evidence. The Task 10 adversarial self-test fully
rebinds a copy and proves that completed-looking reverse-rejection facts also fail closed.
