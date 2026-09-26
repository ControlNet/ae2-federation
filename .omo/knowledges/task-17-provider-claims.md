# Task 17 Provider Claims and Target Binding

## Scope

Task 17 adds five-plus-one Provider node exposure, stable typed Provider/Endpoint identities, Endpoint-authoritative Claim
compare-and-set state, overlap/separation checks, and an authorization gate from Task 16 native Lanes to an Endpoint target.
It does not add Task 18 Endpoint operating modes/return adapters or Task 19 recovery and replay behavior.

## Ownership Invariants

- `ProviderIdentity` is a UUID-backed `ProviderId` plus positive `ProviderInstanceEpoch`; position is not identity.
- `EndpointIdentity` has the equivalent Endpoint ID and instance epoch.
- `EndpointClaimAuthority` owns one `ClaimState`. Acquisition requires the exact current Claim epoch and advances it once.
- An owned Claim retains the exact Provider identity. Offline state does not release it, and no TTL or automatic steal exists.
- `ClaimStateCodec` persists Endpoint identity, Endpoint epoch, Claim epoch, and exact Provider identity/epoch when owned.

## Native Binding Boundary

`ProviderTargetAuthorization.resolve` fails closed until all of these are current: rotation settlement, loaded positions,
Endpoint capability identity, Claim owner/epoch, exposed subnet node and native storage identity, distinct source/target Grids,
non-overlapping Endpoint target domains, settled native network identities, common Fabric, and active Processing Policy with
EXECUTE and SUPPLY operations.

`NativeProviderLaneComposition.bindTarget` associates a Lane with that resolver. The `PatternProviderLogic.findAdapter`
Mixin uses AE2's public `PatternProviderTarget.get` only for an authorized result. An unauthorized or paused result returns
no target and does not search another position. AE2 remains the owner of native execution and remainder state.

`ProviderRuntime` is the production owner of `ProviderNodeWiring` and binds every mapped native Lane to the authorization
resolver. `ProcessingRegistration` registers `EndpointTargetCapability.BLOCK` during NeoForge startup, while
`EndpointTargetBinding` exposes the authoritative Claim and native Endpoint node at the loaded target position.

Do not place compatibility helpers in `appeng.*`: NeoForge's module layer rejects split packages before Mixins apply.

## Evidence Contract

The exact case set is `provider.orientation`, `claim.compete`, `claim.offline-owner`, `claim.overlap`,
`provider.rotate-pending`, and `provider.reject-same-grid`. `verifyTaskSeventeenEvidence` is shared by fresh production and
persisted consumption and correlates every semantic property with its runtime trace. The self-test fully rebinds report
identity and hashes before probing double winners, offline auto-steal, fabricated separation, stale epochs, same-Grid
activation, and missing native traces. Runtime semantics additionally require Provider binding, Endpoint capability lookup,
the expected authorization state, bound Mixin entry, authorized native target lookup counts, and target mutation.

Verification commands:

```bash
./gradlew :neoforge-1.21.1:federationVerify -Pcases=provider.orientation,claim.compete,claim.offline-owner,claim.overlap,provider.rotate-pending,provider.reject-same-grid -PevidenceDir=.omo/evidence/task-17 --no-configuration-cache
./gradlew :neoforge-1.21.1:federationVerifyEvidence -PresultFile=.omo/evidence/task-17/attempt-20260915T011829131Z/result.json --no-configuration-cache
./gradlew :neoforge-1.21.1:federationTaskSeventeenEvidenceSelfTest -PresultFile=.omo/evidence/task-17/attempt-20260915T011829131Z/result.json --no-configuration-cache
```

Expected signals are `BUILD SUCCESSFUL`, `Federation evidence verified`, and
`Task 17 adversarial Provider Claim evidence probes were rejected for intended reasons`.
