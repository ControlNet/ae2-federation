# DESIGN v0.4 planning grounding

## Request and workflow status

- User explicitly replaced the previous design and requested replanning from `DESIGN.md` v0.4.
- Full current document read: 1851 lines. Current design is authoritative over earlier notes and researcher recommendations.
- Intent: clear. Classification: architecture. High-accuracy review not explicitly requested.
- Prior v0.2 questions were unanswered. The latest user reply explicitly confirms Multipart Bridge, Interface-like five-face Endpoint, native recovery orchestration and TDD; earlier incompatible defaults remain rejected.
- User's latest '开始制订计划' authorizes formal plan creation from the accumulated brief and clarifications. No implementation is authorized and no product code was edited.
- Parent has no command-execution tool, so the mandatory ulw-plan scaffold remains unexecuted. This file is an evidence note, not a scaffold-compliant draft or an approved plan.

## Confirmed scope

1. Build/runtime/test baseline, persistent network identities, Fabric topology and world-scoped sparse network-pair Policy.
2. Processing: real native Provider execution contexts per Endpoint Lane, local/federated Endpoint, claims and native return paths.
3. Storage: native mounts/access delegation, provenance and duplicate-source handling, cross-Fabric dependency/subscription optimization, explicit chain-sharing switch and registered addon resource types including stored FE.
4. Crafting/Automation: actual native planner/CPU/provider/request/result flows; no independently imposed supply or delivery contract.
5. ME network energy sharing: real native energy operations, directional policy and grid/channel isolation; Fabric itself consumes zero energy.
6. Fabric-scoped unified Hub/Bridge management, observability, actual-client automation, compatibility and continuous factory benchmarks.

## Hard corrections from v0.2

- No Matrix, global terminal, handheld manager or player/team ACL.
- Same-dimensional wired prototype, no wireless/cross-dimensional access or active chunk loading.
- All players can manage entrances; server-side object/context/revision/Policy/Claim validation remains mandatory.
- One Provider Federation face, other five faces share one local ME node; rotating preserves persistent identity and existing responsibility.
- Policy is globally unique by consumer/provider network identities and capability rules, not by Bridge/Fabric/path. Missing rules deny sharing.
- Enabled is persistent configuration; active is derived from enabled and at least one common confirmed Fabric. Backend readiness is separate.
- Multiple common Fabrics or Bridges are redundancy, never duplicate inventory, registrations or throughput allowance.
- Chained Storage access cannot activate a direct Policy whose endpoints have no common Fabric.
- Policy survives line changes, all old Bridge replacement, saves and inactive periods; explicit disable/delete cannot be resurrected by stale caches.
- Native runtime objects own locks, tasks, input remainders, returns and inventory; use their persistence. Federation stores configuration, binding and only necessary associations, not duplicate business state.
- Prototype visuals are explicitly provisional, user-authorized assets, not finished art. No fake implementation or fake compatibility evidence is authorized.

## Directly verified source facts

AE2 release reference: `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a` (researcher associates this with NeoForge 19.2.17).

### Native Provider composition

Directly read:
https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java

- Independent native logic instances already have independent native lock, send and return state. A custom Federation lock state machine is not required merely because one physical block exposes many lanes.
- Constructor allocates an `AppEngInternalInventory`, registers a provider and ticker on the supplied managed node, and applies REQUIRE_CHANNEL.
- Target discovery and return-lock transition are private. Integration needs an evidence-backed composition boundary, not an assumed overridable target method.
- `updatePatterns()` decodes every item in its private pattern inventory and calls node-provider refresh. Global registration alone is insufficient: refresh routing must also be adapted.
- A shared unfiltered physical inventory assigned to every lane would incorrectly advertise all patterns to all endpoints and expand each lane's Blocking input set. Each native execution context needs its mapped pattern view, without multiple extractable inventories.
- Native NBT and drops include pattern inventory. Calling them naively for every lane can duplicate item serialization/drop ownership. Native business-state persistence must remain while physical pattern ownership is serialized/dropped once.
- `pushPattern` checks adjacent `ICraftingMachine` before `findAdapter`. A hook only at `findAdapter` does not by itself prevent unintended local crafting-machine dispatch. Endpoint binding must fail closed and never fall back to local targets for a federation lane.
- `sendStacksOut` resolves by saved direction; orientation changes must not retarget an already-owned remainder.
- Batch checks are per-key simulation followed by actual insertion and native `sendList` handling. Neither generic transactions nor a Federation reservation engine follow from this API.

### Persistent network identity extension

Directly read:
https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/networking/GridServices.java
https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/networking/IGridServiceProvider.java
https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/me/Grid.java

- Public `GridServices.register` constructs a registered service for each Grid, not only grids containing a Federation block.
- `IGridServiceProvider.saveNodeData` explicitly supports namespaced provider-specific node data; `addNode` receives that saved data. Grid invokes these hooks for its nodes.
- This is a concrete native extension candidate for identity continuity through surviving native nodes; a new anchor block or manual token must NOT be declared necessary merely because AE2 has no built-in persistent NetworkId.
- Runtime proof is still needed: host dirty marking/save timing, full removal of Federation devices, world restart, partial chunk loading, node transfer between grids, copied nodes, true splits/merges and ambiguous lineage. Unknown identity must not grant old Policy.
- Earlier researcher claim that all-access-block removal necessarily requires a new anchor block is rejected as unsupported/incomplete.

### GUI tooling

Directly read:
https://raw.githubusercontent.com/Low-Drag-MC/LDLib2/52dcc476975a5bcea48b6f8aa1a5dab9fc67060c/gradle/ldlib2-uitest.gradle

- This script explicitly targets NeoGradle-style top-level `runs`, not automatically the project's selected ModDevGradle DSL. Reuse harness runtime/properties while adapting build wiring deliberately.
- Missing reports fail; the source also clears old output before runtime dependency resolution. Project QA must bind evidence to the current run and reject stale/zero-scenario success.
- Headless/REAL-input conflicts and GL-context differences are documented in the script. Prefer qualifying Xvfb + synthetic input for actual-client QA rather than assuming displayless rendering works.
- Do not infer that latest-source flags exist in an older artifact without checking its matching source/bytecode.

## Researcher claims retained as candidates, not runtime proof

- Candidate dependency tuple: MC 1.21.1, NeoForge 21.1.250, AE2 19.2.17, LDLib2 2.2.34, MDG 2.0.146, Gradle 9.2.1, Java21. No combined build/run was performed. Do not downgrade Gradle just because an older documentation example names 8.8.
- Researcher reports LDLib2 coordinate `com.lowdragmc.ldlib2:ldlib2-neoforge-1.21.1:2.2.34:all` exists, includes harness classes, SHA-256 `5314e624e3b4258812a881b3a52886158af53886e5fb96d34919bdf0d5a374d6`. Parent has not independently hashed the JAR.
- CELLS reference commit `645901c954d6f9b5e413bbba2202e471beab8141`: origin election, bounded event deduplication, snapshot reconciliation and listener cleanup supply regression cases, not a source-portable dependency or permission to copy one-hop limits.
- Directional ME energy must not be silently implemented as symmetric Quartz Fiber overlay. Verify the actual operation boundary; no duplicate energy ledger.
- Native Crafting services and links have grid-local assumptions. Neither global provider registration nor a renamed remote requester protocol establishes a compliant full integration. Keep complete native capability discovery/planning/materials/results/cancellation as an early proof gate.

## Approved planning direction

- Retain full six-capability prototype scope and M0-M6 dependency sequence, with native-composition/identity/crafting/energy feasibility checked in M0 rather than discovered after UI completion.
- M1 brings global Policy and unified functional Hub/Bridge management forward; M5 expands visualization, not first management access.
- Add explicit native-call trace and unique-state-authority checks T-C06..08, Policy lifecycle T-G01..14 and prototype-boundary T-B01..09.
- Do not turn unresolved feasibility into instructions to invent a parallel engine, weaken scope or introduce anchor/controller blocks.
- Bridge physical-topology clarification resolved: direct ME-to-ME only, never Federation Cable. General access is now explicitly assigned to Hub; do not repurpose Bridge or add another access device.

## Post-approval gap review

- Metis review completed in session `ses_f69531125ffej6tJoUgCMtuY9I`; read-only, not high-accuracy dual review.
- One product topology blocker remains: the approved direct-only Bridge cannot provide the general ME-to-Hub/Cable attachment assumed by earlier DESIGN prose. Provider and Endpoint provide approved processing attachments, but general Storage/Crafting/Energy exposure through those ports is not explicitly confirmed. Hub currently has Federation-only responsibilities in the design.
- Do not invent a new adapter block, repurpose Bridge, silently add Hub ME ports, or defer an unresolved product choice to the executor. Ask which existing component provides this general attachment before claiming a decision-complete full plan.
- Technical proof gates remain native Lane composition, native Crafting full lifecycle, and Endpoint capability composition. They require exact success/failure evidence, not permission to replace native engines.
- Owner resolved the remaining topology blocker: Hub is the complex Fabric access device; each of its six faces accepts either a native ME network connection or Federation Cable. Preserve separate ME domains across faces; deduplicate multiple attachments to the same actual network. Face classification follows actual neighbor connection, not a new manual face-role configuration requirement.
- Current next action: finish the approved full plan. Approval is preserved; do not restart the previous interview.

## Latest owner clarification (supersedes earlier layout/recovery proposals)

- Bridge is an AE2 Multipart mounted on an ME cable, with its other end connecting the other independent network's ME cable. Not a full block with two ME faces and four Federation faces.
- Latest correction: the opposite ME attachment is not restricted to cable; an ME device exposing a valid sided native ME connection can attach to its own network. Actual sided grid connectivity, not block name alone, determines compatibility.
- User reiterated that ME Cable is included, not excluded: the opposite endpoint may be a cable or another native-connectable ME device in a different network. Reuse AE2 native sided node discovery/connection and lifecycle precedents rather than maintaining a Federation whitelist of device classes. This is a clarification of the existing constraint, not a new product fork.
- User explicitly selected ME Toggle Bus as the connection and cable-extension visual precedent: both opposite cable and ordinary connectable ME block devices are supported through native attachment behavior.
- Directly verified `ToggleBusPart.java` at AE2 commit `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`: creates a sided in-world outer managed node, exposes it through `getExternalFacingNode`, persists/destroys it through native lifecycle, and supplies `getCableConnectionLength`. Its `updateInternalState` additionally connects main and outer nodes using `GridHelper.createConnection`; Federation must NOT inherit that cross-boundary connection or its redstone-switch semantics. Reuse attachment/discovery/render integration while leaving the two network domains separate.
- A non-null outer node alone is insufficient evidence of a connected opposite ME network: validate actual external attachment, so the part's own isolated outer-node Grid cannot make an empty end appear valid.
- Bridge never connects to Federation Cable or acts as a ME-to-Federation adapter. Both ends must resolve to two distinct actual ME networks; missing either network, unsupported attachment, or both ends resolving to the same Grid disables bridge operation.
- Revalidate both native network bindings on neighbor/capability/lifecycle/Grid changes. Disabled bridge supplies no effective two-network Fabric connection. Other valid bridges may still sustain the same global Policy relation.
- Operational disable is not a player edit to Policy.enabled: preserve world-level Policy and derive active from remaining valid connectivity. Never delete rules or replay native work when the bridge becomes valid again.
- Add TDD cases for cable/device attachment, each missing side, Federation Cable rejection, same-Grid rejection, valid reconnection, runtime merge/split and redundant bridges; preserve native business-state authority.
- Endpoint has one Federation-cable face. The other five faces accept subnet ME cables or logistics pipes, analogous to an ME Interface. Do not reserve a dedicated rear ME-only face or require manual return-face configuration by default.
- Recovery, dismantling and interrupted crafting should follow verified AE2 native orchestration. User rejected the need to design a new default sealed-recovery item or forced-drain gameplay. Preserve only unavoidable Federation mapping/identity glue.
- TDD explicitly approved.
- User explicitly requires NeoForge GameTest consideration; adopted as the primary bounded in-world integration framework in the plan, not just an optional dev testmod. Official 1.21.1 docs confirm `RegisterGameTestsEvent`, `@GameTest`, `GameTestHelper`, explicit namespace filtering, singular `data/<namespace>/structure` templates and `runGameTestServer` required-failure exit behavior. ModDevGradle uses `neoForge.runs` with `type = "gameTestServer"`; do not copy NeoGradle-only DSL/workarounds. JUnit stays for pure logic; LDLib2 for actual client UI; real process/long-world tests for restart/soak. GameTest loaded test regions cannot prove natural unloading.
- User's example of accessing subnet craftables is a requested behavioral reference to investigate, not proof that an unconfigured Interface/Storage Bus alone exposes remote crafting in the selected AE2 version.

## Capability separation evidence for Endpoint

Directly read `InitCapabilityProviders.java` at AE2 commit `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`:
https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/init/InitCapabilityProviders.java

- Native Interface exposes both ME_STORAGE (InterfaceLogic.getInventory) and GENERIC_INTERNAL_INV (InterfaceLogic.getStorage).
- Native Provider exposes GENERIC_INTERNAL_INV backed by its native return inventory.
- AE2 supplies ItemHandler and FluidHandler adapters for registered generic inventories.
- Thus a face need not have one exclusive logical role: subnet ME connections, native ME_STORAGE input access and logistics return capabilities can be distinguished by capability, not arbitrary pipe-resource guessing. Endpoint-specific bindings and local Provider handling still need runtime verification.
- No assertion that every third-party pipe follows the same capability selection is permitted. Test item/fluid and AE2-capability-aware callers separately.
