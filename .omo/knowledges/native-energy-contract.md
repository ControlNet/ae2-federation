# Native Energy Contract

## 2026-09-14 Task 10 Qualification

- AE2 19.2.17 shares power between distinct data Grids through the internal
  `IEnergyOverlayGridConnection -> EnergyService` composition used by Quartz Fiber.
- A finite `EnergyCellBlockEntity` gives exact `IAEPowerStorage.getAECurrentPower()` debit observations. Consumer
  `IEnergyService.extractAEPower` has no separate target-receipt transaction.
- A one-sided relation appears directional only before overlay discovery. Once the consumer builds the overlay,
  `EnergyOverlayGrid.buildCache` assigns the same overlay to every discovered service and reverse extraction succeeds.
- Exact missing hook: shared `EnergyOverlayGrid` has no caller/edge direction, route identity, or authorization boundary.
- Cold-start public power requires AE2's greater-than-30-tick stabilization after shared power becomes available.
- Rings terminate by service identity deduplication and conserve finite stored energy, but deduplication is not policy.
- The valid outcome is schema-v3 `BLOCKED` after all five GameTests execute. Persisted completion consumption and fully
  rebound completed-looking reverse evidence must both reject.
- Fabric control remains zero idle power through `GridFlags.CANNOT_CARRY`; the no-source case proves this is not generated energy.
