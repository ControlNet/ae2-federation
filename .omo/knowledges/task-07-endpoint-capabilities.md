# Task 7 Endpoint Capabilities

- Pinned AE2 `19.2.17` `PatternProviderTargetCache.find` resolves `AECapabilities.ME_STORAGE` before generic platform
  inventories. A directional native Pattern Provider can therefore target an unconfigured native Interface while its
  target face remains excluded from Provider Grid connectivity.
- The native Interface's `ME_STORAGE` view is its subnet Grid inventory when no stocking configuration exists. The five
  non-Federation Endpoint faces can share that one node and storage domain without adding an Endpoint inventory.
- Native Provider returns are owned by `PatternProviderLogic.getReturnInv()`. It is a `PatternProviderReturnInventory`
  whose insertion guard prevents capability feedback during injection to the Provider Grid.
- AE2's `GenericStackItemStorage` and `GenericStackFluidStorage` are the pinned generic-inventory adapters. Endpoint
  return routing should delegate to those adapters over the identified Provider return inventory, not inspect resources.
- Local ownership requires exactly one Provider, source/subnet Grid inequality, and no data edge on the Provider target
  side. Typed input/return contexts plus mode generation prevent capability loops and stale Local/Federated crossover.
- Task 7 evidence uses exact `endpoint.*` cases, separately hash-bound `AE2F_ENDPOINT_TRACE` facts, and fully rebound
  missing/duplicate/Grid/two-owner/loop/mode/face adversarial mutations.
- Direct Mixin probes at `PatternProviderLogic.pushPattern` and `PatternProviderTargetCache.find` record native owner
  identities into the hash-bound runtime log. Persisted consumption correlates those identities with the Local-native
  properties artifact, so method-name labels alone cannot satisfy the native-entry proof.
- The five-face fixture also compares the composed adapters with NeoForge's actual sided `ItemHandler` and `FluidHandler`
  lookups on the pinned native Provider. Both paths insert into the same identified Provider return inventory.
- Final current-source schema-v3 attempt: `.omo/evidence/task-07/attempt-20260914T003210719Z/result.json`. Canonical
  persisted consumption and `federationTaskSevenEvidenceSelfTest` both pass against this attempt.
- Independent review correctly rejected caller-asserted ownership. `EndpointCapabilityComposition` now owns the native
  `ServerLevel` and Endpoint position, requires `providerPosition.relative(targetSide) == endpointPosition`, binds that
  world block entity's exact `PatternProviderLogic`, resolves the Endpoint-side exposed node, and requires its sided
  `ME_STORAGE` capability to be the subnet Grid inventory. A live remote Provider is rejected before the adjacent one binds.
- Five-face proof now calls `faceNode(face)` and `faceStorage(face)` separately for DOWN/UP/NORTH/SOUTH/EAST. Each call
  performs a sided AE2 exposed-node and `ME_STORAGE` lookup; all identities converge on one subnet node/storage domain.
  WEST, the Federation face, resolves no node, storage, item-return capability, or fluid-return capability.
- Task 7 native-entry parsing now lives inside `verifyTaskSevenEvidence`. Fully rebound push/target owner probes forge the
  properties artifact and ordinary `AE2F_ENDPOINT_TRACE` fact together while leaving the independently emitted Mixin
  entry unchanged, and must fail specifically with `Task 7 native method entry identity mismatch`.
- Final repaired current-source attempt: `.omo/evidence/task-07/attempt-20260914T011114579Z/result.json`.
