# Task 31 Directional ME Energy

- A consumer-local read-only `IAEPowerStorage` on an existing Bridge/Hub managed node lets AE2's native `IEnergyService` discover Federation supply, including a cold Grid. Posting `GridPowerStorageStateChanged(PROVIDE_POWER)` after binding publication wakes native power evaluation.
- Authorization is an ordered `PolicyKey(consumer, provider, ME_POWER)` with `SUPPLY`; bindings capture the Policy revision, Fabric references/topology revision, exact runtime Grids, exact provider `IEnergyService`, and provider-source generation.
- Provider discovery excludes Federation `DirectionalEnergySource` nodes and fingerprints native public extraction sources by stable node identity plus exact node/storage objects. Rediscovered immutable source descriptors must compare by value or every reconciliation spuriously advances generation.
- Extraction remains native: consumer demand calls the local source, the binding revalidates all captured authority, and the exact requested amount is extracted from the provider's native service. Simulation is non-mutating and no tax, reserve, or settlement ledger exists.
- `EnergyRouteGuard` bounds recursive multi-route/ring traversal by ordered Policy key. Fabric removal, Policy revision/deletion, provider replacement, unload, and level close make captured bindings fail closed.
- Task 10's `energy-proof.*` cases remain the blocked symmetric-overlay characterization. Task 31 owns separate `energy.*` cases and a separate semantic verifier.
- Canonical QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=energy.directional-policy,energy.cold-start,energy.ring-conservation,energy.reject-reverse,energy.disconnect-no-source -PevidenceDir=.omo/evidence/task-31 --dependency-verification=strict --no-configuration-cache`.
- Backend source removal/replacement must be reconciled from the native demand path as well as topology/Policy events. A generation mismatch must remove the old binding before reconciling so stale bindings release their Grid, service, node, and storage references; availability events must not be posted while AE2 is actively extracting.
- The canonical ring is three independent Hub-attached native Grids with ordered `A <- B`, `B <- C`, and `C <- A` `ME_POWER/SUPPLY` rules. Charging only B with 300 AE and requesting 400 AE from A proves recursive traversal terminates, exactly 300 AE is accepted/debited, and no energy is created.
- Task 31 authority is not the final evidence map. `AE2F_ENERGY_AUTHORITY` captures current/withdrawn binding identity before operations, while `AE2F_ENERGY_OPERATION` captures native service/request/acceptance and before/after values at operation time. Persisted verification enforces exact receipt schemas, phase sets, child identity, ring identities, generations, and arithmetic correlations.
- Canonical repaired evidence passed at `.omo/evidence/task-31-final/attempt-20260921T012646078Z`; the persisted consumer and rebound mutation suite also passed. Task 14 remained green. Task 10 remained intentionally BLOCKED by its frozen shared-overlay characterization and was not altered.
- The truncation proof uses AE2's production `EnergyCellBlock` and `EnergyCellBlockEntity`, registered by the testmod with a finite 2,000,000,000 AE capacity. The canonical directional case preserves its 250 AE operation, then simulates and modulates 1,000,000,250 AE through the consumer Grid's native service; exact provider availability, unchanged simulation balance, acceptance, and debit are persisted and receipt-bound.
- Final-review evidence is intentionally retained under `.omo/evidence/task-31-final-review/` for Atlas and independent review. Its mutation suite must reject a fully rebound `largeAccepted=1000000000.0` value, proving the semantic threshold rather than merely matching text.

## 2026-09-23 large observation regression

- `EnergyBindingService.nanoAe(1_000_000_250.0)` yields 1,000,000,250,000,000,000 nano-AE. The earlier
  `FlowState` validation used the 9e15 item/fluid observation cap for all units, so the accepted native operation
  threw after debiting the provider. A meter unit test was red before the change and green afterward.
- `NANO_AE` now has its own finite 9e18 limit while ITEM and FLUID_DROPLET remain at 9e15. The focused meter tests
  assert the energy transfer, its own overflow rejection, and the unchanged fluid bound. The canonical five-case
  Task 31 verifier passed at `.omo/evidence/task-31-energy-observation/attempt-20260923T045856005Z`; its directional
  receipt records `largeAccepted=1.00000025E9` and an equal provider debit. Task 35 packet/release verification also
  passed at `.omo/evidence/task-35-energy-observation-regression/attempt-20260923T051914465Z`.
