# Task 25 Resource Qualification

## Native AE2 resources

Task 25 qualifies AE2 19.2.17 item and fluid keys on Minecraft 1.21.1 / NeoForge 21.1.250.
`AEItemKey` identity includes data components: two diamond stacks with different `CUSTOM_NAME` components remain distinct
before and after `AEKey.toTagGeneric` / `AEKey.fromTagGeneric`. `AEFluidKey` uses native long quantities measured in
millibuckets. Both key types are exercised through physical AE2 cell-backed `MEStorage`, native type and exact-key filters,
simulation, modulation, listing, and extraction.

`NativeResourceAmounts.checkedAdd` rejects negative quantities and `long` overflow. It performs no unit conversion. Stored
resources and the AE power service remain separate: no item/fluid operation changes an `IAEPowerStorage`, and an AE power
balance cannot create a resource key.

## Applied Flux 2.1.4 qualification

| Field | Re-verified value |
|---|---|
| Repository | `https://github.com/GlodBlock/ExtendedAE` |
| Branch | `appflux/1.21.1-neoforge` |
| Matching source commit | `a54eafb72d72bd259bc3b5fa226b4f5542c4c3c4` |
| Branch head at verification | `d7488e01e29a49c65a8f2fb9a453f08ae723d6cd` |
| Source version at matching commit | `1.21-2.1.4-neoforge` |
| Source license | `LGPL-3.0-only` |
| Source Minecraft / NeoForge | `1.21.1` / `[21.1.113,)` |
| Source AE2 range | `[19.2.2-beta,)` |
| Modrinth version ID | `sD979rMC` |
| Live Modrinth title/version | `AppFlux 1.21-2.1.4-neoforge` |
| Downloaded filename | `AppliedFlux-1.21-2.1.4-neoforge.jar` |
| Size | `337796` bytes |
| SHA-1 | `741e856b0c928fc15b59e92d98ea75ef98541ee8` |
| SHA-256 | `2d5c0dfbf1853e28d515b4224ca39a1de4520a1ac2e0fe987e6400965bf1f555` |
| SHA-512 | `5dc7119bf192ee798b65dea7f28e279b74ef371a8e59f5147518dd7cddfcc3d45fd6161ce48815ff07f756a3226819f0ca03f62c8549dfe4d8ecc6191f70bd64` |
| Modrinth dependency metadata | Empty |
| JAR-required mods | AE2, GuideME, Glodium |

Commit `a54eafb...` is the explicit one-line version bump from 2.1.3 to 2.1.4. That tree defines the `appflux` mod,
Minecraft 1.21.1, NeoForge `[21.1.113,)`, AE2 `[19.2.2-beta,)`, and LGPL-3.0 metadata. It also defines
`FluxKeyType extends AEKeyType`, registers `FluxKeyType.TYPE` through
`AEKeyTypes.register`, exposes `FluxKey.of(EnergyType.FE)`, and stores cell quantities as `long`. The artifact's embedded
NeoForge metadata independently confirms Minecraft 1.21.1, NeoForge `[21.1.113,)`, AE2 `[19.2.2-beta,)`, GuideME, Glodium,
and LGPL-3.0.

The official Modrinth project links the same GitHub repository. Version `sD979rMC`, its filename/version metadata, the
artifact's embedded metadata and implementation, and the source tree at `a54eafb...` agree. Its 2026-02-20 publication
follows the 2026-01-07 version bump; intervening public branch changes after that bump are resource-only localization
changes. This is an authoritative ordinary source-artifact correlation, not a reproducible-build or cryptographic
source-to-binary identity claim.

## Isolated compatibility runtime

The `appfluxTest` source set and `runAppfluxGameTestServer` run resolve only pinned Applied Flux
`maven.modrinth:appflux:1.21-2.1.4-neoforge`, GuideME `21.1.1`, and
`maven.modrinth:glodium:1.21-2.2-neoforge`. Exact JAR and POM
SHA-256 values are pinned in Gradle dependency verification metadata. The source set is associated with the test mod only
when `-PenableAppfluxCompatibility=true`; default main, testmod, runtime, production JAR, and sources JAR remain addon-free.
GuideME 21.1.1 is the version already selected by AE2 19.2.17 and satisfies AppFlux's unbounded `guideme` requirement;
Glodium 2.2 satisfies `[1.21-2.0-neoforge,)`.

| Isolated artifact | Authoritative release identity | Pinned JAR SHA-256 |
|---|---|---|
| Applied Flux 2.1.4 | Modrinth `sD979rMC` | `2d5c0dfbf1853e28d515b4224ca39a1de4520a1ac2e0fe987e6400965bf1f555` |
| GuideME 21.1.1 | Maven Central `org.appliedenergistics:guideme:21.1.1` | `62229015025b7c0a741590b626b6719631f6b8a945c483ece12e7035d4fd903d` |
| Glodium 2.2 | Modrinth `pfbmdJ3b` | `369e99753df0bdc90a38a8c52df4abb37baf6b18c6ca130af4c7bc75fb160a85` |

The compatibility GameTest strongly imports the real API, verifies registered type `appflux:flux`, creates
`FluxKey.of(EnergyType.FE)`, performs generic AEKey codec and native type/exact-filter checks, and obtains the registered
`FE_CELL_256M` inventory through `FECellHandler`. It simulates and modulates insertion of `4,294,967,311` FE, lists the
same long quantity, simulates and modulates extraction of `1,073,741,829` FE, and observes `3,221,225,482` FE remaining.
The operation leaves AE power at exactly 1,000 AE and no AE-to-FE conversion or synthetic inventory occurs.

## Verification

```bash
./gradlew :neoforge-1.21.1:federationVerify \
  -Pcases=resources.item-fluid-components,resources.stored-fe,resources.optional-absent,resources.reject-overflow,resources.reject-fe-power-coupling \
  -PevidenceDir=.omo/evidence/task-25 --dependency-verification=strict --no-configuration-cache --warning-mode fail
```

The command exits zero only after all five GameTests pass and writes a schema-v3 `complete` result. Use the reported
`result.json` with the generic consumer and Task 25 adversarial verifier:

```bash
./gradlew :neoforge-1.21.1:federationVerifyEvidence \
  :neoforge-1.21.1:federationTaskTwentyFiveEvidenceConsumer \
  :neoforge-1.21.1:federationTaskTwentyFiveEvidenceSelfTest \
  -PresultFile=.omo/evidence/task-25/attempt-<run-id>/result.json \
  --dependency-verification=strict --no-configuration-cache --warning-mode fail
```
