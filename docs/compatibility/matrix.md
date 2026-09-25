# Actual-Mod Compatibility Matrix

This matrix is limited to Minecraft 1.21.1 on NeoForge. A pinned artifact is not a support claim: only an actual isolated
runtime scene can qualify a row. No optional mod is bundled with AE2 Federation.
The capability-backed machines in internal GameTests are test-only fixtures, not third-party compatibility. Prototype block and part visuals are provisional; neither a model in the JAR nor a synthetic client screenshot qualifies an external addon.

| Combination | Artifact SHA-256 | Source/license checked | Native capability path | Result |
|---|---|---|---|---|
| Applied Flux 1.21-2.1.4 + AE2 Federation | `2d5c0dfbf1853e28d515b4224ca39a1de4520a1ac2e0fe987e6400965bf1f555` | ExtendedAE `a54eafb72d72bd259bc3b5fa226b4f5542c4c3c4`; LGPL-3.0-only | `IStorageProvider.mountInventories` to the real `FE_CELL_256M` | **QUALIFIED**: native and Federation layouts use the same cell backend and preserve `appflux:flux`, accepted amounts, and final state. |
| Functional Storage 1.21.1-1.3.3 + Titanium 1.21-4.0.12 | `974a1b0e45e98a9769e84fdfe4d7e3eac3de3936ed7a9baf009026ebee98fc12`; Titanium `b004f9070bd3f2ef55132fee6f071c064b75d4088cfc40936c36c9014a370c2b` | Functional Storage `dcbe25d16dde941d1d2a1a5b1123b2c9fac33f35` (MIT); Titanium artifact metadata says GNU Lesser General Public License; Modrinth version ID `qyocTQUb` | actual `DrawerTile` item capability through AE2 Storage Bus | **QUALIFIED** within the complete Task 36 five-case matrix: an isolated GameTest mounts the actual drawer through a seeded, connected native AE2 Storage Bus at priority 100. Native and Federation operations each insert 16 and extract 4 into the same callback-owned mount and leave 12 in the drawer. The completed producer requires this child and its owner/backend hook alongside both real logistics differentials. The earlier `jH3wVEds` ID names 1.3.4 despite its 1.3.3 JAR filename; it is not this pin. |
| Pretty Pipes 1.21.1 + Pretty Pipes Fluids 1.21.1-3.1.0 | `6559ba086101f955b83577f2bbe9c2b7bf829e71acfea8728d07da7a0ffa09c9`; fluids `925a34a87ed8036e28fdbde8c49132210e664971a99669e94d0ffd42bf54c256` | Pretty Pipes `f7232f61f9e4f6976c8af40f9bf93cb6f2c420f0`; fluids `8f0e0ee3873fed150208f057aabaac0b03581939`; MIT | connected native item and fluid pipes; AE2 Storage Bus over real chest and Sky Stone Tank capabilities | **QUALIFIED** for the pinned near-origin isolated GameTest scene: three fresh item runs and three fresh fluid runs each delivered into the physical chest/tank in both layouts within the identical 120-tick budgets. The exact native Storage Bus callback delegate and owner are retained by the Federation source domain. Native/Federation extractions are 4 ingots and 250 mB respectively, leaving 12 ingots and 750 mB water. |
| Recursive AE2 Pattern Provider 1.0.8 | Not used | published source metadata says All Rights Reserved | native Provider extension | **BLOCKED**: license does not authorize use in this qualification. No artifact or mock is substituted. |
| GTCEu 1.21.1-7.0.2 | `c09a550523e931342a575193cbaad2e4b643e5de2cefe280cd1de540022c6388` | tag `v7.0.2-1.21.1`, commit `f6200e018582c3e26e8db06c289d9e01c8250ac8`; LGPL-3.0 code, separately credited assets | published machine item/fluid capabilities | **BLOCKED_STARTUP_CLIENT_CLASS_LOAD** for the exact pinned server tuple: mod construction at `CommonInit.init(CommonInit.java:177)` throws `Attempted to load class net/minecraft/client/multiplayer/ClientLevel for invalid dist DEDICATED_SERVER`. The nested Gradle task reports success despite FML aborting before GameTests; no machine recipe ran. This is an observed startup boundary, not a machine-compatibility result. |

Pipez and Sophisticated Storage are unsupported. They are not aliases for the qualified rows and are not replaced by test
fixtures. `compat.reject-unsupported` verifies that a blocked row cannot be promoted to a support claim.

## Reproduction

```bash
./gradlew :neoforge-1.21.1:federationVerify \
  -Pcases=compat.native-differential,compat.provider-hooks,compat.real-tech-line,compat.optional-absent,compat.reject-unsupported \
  -PevidenceDir=.omo/evidence/task-36
```

This five-case run executes the Applied Flux, Functional Storage and both logistics children, binds the real GTCEu
startup abort to its pinned artifact and loader/AE2 tuple, and produces a schema-v3 result for persisted consumption.
The independently pinned isolated logs are `.omo/evidence/task-36-functional-storage-strict.log`,
`.omo/evidence/task-36-pretty-pipes-startup-strict.log`, and `.omo/evidence/task-36-gtceu-startup-strict.log`.
To reproduce the individual diagnostics serially (the published artifacts were downloaded separately and SHA-256 checked):

```bash
./gradlew :neoforge-1.21.1:runFunctionalStorageGameTestServer -PenableFunctionalStorageCompatibility=true -PfederationCompatibilityTestId=compatFunctionalStorage --dependency-verification=strict --no-configuration-cache
./gradlew :neoforge-1.21.1:runPrettyPipesGameTestServer -PenablePrettyPipesCompatibility=true -PfederationCompatibilityTestId=compatPrettyItems --dependency-verification=strict --no-configuration-cache
./gradlew :neoforge-1.21.1:runPrettyPipesGameTestServer -PenablePrettyPipesCompatibility=true -PfederationCompatibilityTestId=compatPrettyFluids --dependency-verification=strict --no-configuration-cache
./gradlew :neoforge-1.21.1:runGtceuGameTestServer -PenableGtceuCompatibility=true -PfederationCompatibilityTestId=harnessNativeSmoke --dependency-verification=strict --no-configuration-cache
```

The GameTest runner normally places structures at random world X/Z coordinates between approximately -15 and +15 million.
Pinned Pretty Pipes stores world positions as `float` and its movement loop caps each substep at 0.25 blocks. At large
coordinates, sub-block movement rounds away: an item replay had 16 ingots held in its source pipe at X=2,329,418 after
120 ticks despite a loaded area. A testmod-only GameTest placement hook selects X/Z=0 for the two logistics cases; it
does not modify the pipe, its transport speed or its destination inventory. Both scenes assert their physical near-origin
position and still require real source extraction, connected-pipe delivery and identical native/Federation debits.

GTCEu embeds `Registrate MC1.21-1.3.0+67` (`510f4041c41739f1d8ea8850ab8364d3e3a5fada8529beed5d9f479e2523db52`),
`LDLib 1.0.35.a` (`6e95c29c650884b248038f45f086c6bd54fcc2a4ae9cf1034a4080ebaea052`) and
`Configuration 3.1.0` (`c5cde51eecd1741d22521c05f1f93fdc8b6ab7dfe19f0988d65e0e73d5044e34`).
These nested archives were extracted from the independently hashed GTCEu JAR and hashed separately. No optional JAR is
included in production main/release archives. All five newly investigated optional JARs and their Maven metadata are pinned under strict
dependency verification; the separate embedded GTCEu dependencies are checked against the hashes above.
