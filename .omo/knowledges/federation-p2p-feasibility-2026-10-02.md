# Federation P2P feasibility investigation (2026-10-02)

## Product discussion location

Following the user's approval to separate ideas from research notes, maintain future product decisions in
[the Federation P2P idea](../../docs/ideas/federation-p2p.md), listed in the
[idea library](../../docs/ideas/README.md). This note retains investigation evidence and discussion history;
its historical proposals do not override the current idea page. Following the user's renewed remote-connection
discussion, the related wireless context is maintained in its own
[remote connections idea](../../docs/ideas/remote-federation-connections.md). The user has since proposed a
quantum-bridge-style jump as a first candidate and requested research into other forms; naming, appearance and
detailed form remain undecided. See also [the wireless mod survey](wireless-tech-mod-survey-2026-10-02.md).
Consult that page for current product discussion rather than inferring decisions from earlier research proposals.

## Request and conclusion

Discussion boundary confirmed by the user: concrete implementation details are deferred to the future coding agent.
Current discussions should capture intended player behavior and product constraints. Class names, registry structure,
address representation, callbacks, cache strategy, implementation phases and technical test wiring in this note are
feasibility references, not approved implementation requirements. The future coding agent should evaluate these choices
against the codebase at implementation time. Do not keep asking the user to settle technical details now.

The user proposed an additional AE2 P2P mode that carries Federation cable connectivity and requested investigation.
Same-dimension support is feasible based on the pinned AE2 source and the current Federation topology. This is a
research conclusion, not a working implementation or a user-approved final feature specification. No production code,
DESIGN requirements, new tests, issues or PRs were created. No synthetic implementations or fixtures were introduced.

Reviewed the resolved AE2 19.2.17 source JAR, current NeoForge 1.21.1 integration, and official upstream documentation.
Selected upstream sources were extracted outside the repository under `/tmp/ae2f-p2p-research-19.2.17/` for inspection.
The project's minimum supported AE2 19.2.9 still needs separate compile/runtime qualification for the new feature.

## Upstream mechanisms verified in the pinned source

- `P2PTunnelAttunement.registerAttunementTag(ItemLike)` accepts a registered `PartItem` whose part class extends
  `P2PTunnelPart`. It derives the tag `<namespace>:p2p_attunements/<tunnel-item-path>`. A Federation cable item can be
  the attunement trigger. There is no closed vanilla tunnel-type enum to patch.
- `P2PTunnelPart.onUseItemOn` replaces a tunnel with its attuned type and copies frequency/output state. Its memory-card
  handling exports/imports both the tunnel item type and frequency and can replace the destination's tunnel type.
  Input/output, frequency NBT and frequency model data are already implemented.
- `P2PService` belongs to a native Grid. It tracks one input and multiple outputs per frequency, and discovery checks
  tunnel class. A frequency is not a globally unique link identity: native Grid and tunnel type matter.
- `RegisterPartCapabilitiesEvent.register` can expose the project's sided `FederationPortCapability.BLOCK` from the
  concrete new part class. AE2 resolves the part from the queried side of the cable-bus host. No special patch to the
  host block is needed for that capability registration.
- `P2PTunnelPart` has one channel-requiring main node; its base idle drain is 1.0 AE/t. The ME tunnel subclass overrides
  this to 2.0 and adds compressed-channel flags. Those ME-specific flags and its external Grid nodes are not required
  for a Federation topology tunnel. Final balancing is a product decision and normal server configuration still applies.
- Native transfer taxes are explicit calls from transport implementations, not an automatic charge for every P2P
  capability lookup. A topology-only tunnel need not impose item/fluid/energy transfer tax.
- `AEBasePart.onMainNodeStateChanged`, `onTunnelConfigChange`, `onTunnelNetworkChange`, add/remove lifecycle and neighbor
  callbacks provide suitable integration points. `P2PService.wakeInputTunnels` special-cases `MEP2PTunnelPart`, so a new
  subclass must implement its own response to power/channel/boot state changes. Frequency callbacks alone are insufficient.
- `getInput()` does not establish that both ends are online; outputs also need explicit live/online validation before
  publishing edges. ME P2P's own implementation checks host Grid identity and both endpoints' status.
- `P2PModels` offers reusable status and frequency models around a custom front model.

## Player contract: confirmed topology semantics and proposed interaction

The user subsequently confirmed idea 1 as the agreed product direction before moving on to policy ideation.
This confirmation retains the five decisions below and the implementation-deferral boundary.

The user explicitly confirmed these product decisions after the feasibility discussion:

1. A Federation P2P connection is equivalent to directly connecting its two sides with Federation cable. Its
   connectivity is bidirectional; AE2's input/output roles identify pairing roles, not one-way Federation access.
2. One input with multiple outputs is equivalent to physical cable connections between them. All connected ends
   participate in the same Federation Domain, including output-to-output reachability.
3. The carrier ME Grid only hosts the tunnels and does not automatically become a Federation member. Sharing its
   own capabilities requires a separate explicit Federation attachment.
4. Power and channel requirements should align with AE2's other native P2P tunnels. Use native P2P operating rules;
   the future coding agent should verify the appropriate integration and parameters for the target AE2 version.
   No exact idle-drain value or ME-specific compressed-channel flag was selected by the user.
5. The first Federation P2P release must support cross-dimensional connections within the same server/save. The user
   explicitly selected this scope. A same-dimension-only first release does not satisfy the request. This requirement
   concerns P2P and does not require the separately considered wireless Federation device to ship at the same time.

These confirmations authorize recording the design decisions, not implementing the feature. Cross-dimensional support
is required in the first release; concrete implementation details are deferred to the future coding agent.
The user's agreement with these decisions must not be read as blanket approval of all earlier recommendations.
The attunement interaction below remains the proposed implementation approach.

Register a new part such as `FederationP2PTunnelPart extends P2PTunnelPart<FederationP2PTunnelPart>`, attuned with a
Federation cable. The player uses the existing AE2 memory card to establish one input and one or more outputs on the
same powered/channel-available carrier ME Grid.

The physical front of each tunnel accepts a Federation connection. The native ME Grid carries the tunnel parts;
Federation sees reciprocal virtual edges from the input part to the output parts. Resource operations still follow
the existing Federation native bindings, policies and claims. Do not inherit ME P2P's external native Grid connections:
the front must expose the Federation capability and no external-facing ME Grid node. The carrier Grid does not become
a Federation member merely by hosting the tunnel; membership requires an ordinary explicit Federation attachment.

Although AE2 calls ends input/output for pairing, the confirmed Federation connectivity is bidirectional, like cable.
Directional resource permissions remain the existing network-pair Policy. All outputs belong to the same resulting
connected domain, including output-to-output reachability. A one-way or mutually isolated output design would require
new domain semantics and should not be implied by the word "P2P".

No new permissions are created by connecting domains, but existing enabled historical rules may reactivate. Processing
Endpoints may become reachable by Providers in the joined domain, subject to existing explicit mappings and claims.
Multiple routes must not multiply inventory or capacity. Power loss, channel loss, removal, attunement, frequency
change, native Grid split/merge and chunk unload must remove or rebuild the virtual connection correctly.

## Project integration work

Java paths in this section are relative to `common/src/main/java/space/controlnet/ae2federation/`.

1. **Part registration and presentation.** Reuse `bridge/BridgeRegistration`'s PartItem pattern, register the attunement
   tag during the appropriate setup phase, register the part's capability with AE2, and add name/model/creative exposure.
   Existing AE2 P2P interaction and NBT should be reused. A dedicated P2P management GUI is not necessary for pairing.
2. **Multipart identity is the main topology change.** `FederationDomainNodeId` currently contains only dimension and
   block position. A cable bus can host independent P2P parts on multiple faces. Aggregating all faces into one existing
   node would electrically join every such Federation port because the domain registry treats a node as connected.
   Separate tunnel subnodes need a face/part discriminator, or an equivalent explicit partitioned topology model.
   Changing port-name strings alone does not fix this.
3. **Expose an explicit topology endpoint.** `FederationPort` currently contains only physical position and outward
   face; Cable, Router, Provider and Endpoint derive the remote topology node from its block position. Extend the port
   descriptor with the actual logical topology endpoint (or use an equivalent resolver), so a physical neighbor can
   reach exactly one P2P part rather than its whole cable-bus block. Keep physical adjacency checks for local faces.
4. **Virtual links.** `FederationDomainPortEvidence.Federation` already points to an arbitrary node/port;
   `FederationDomainRegistry.linked` checks reciprocal evidence, not world distance. This is a useful existing base
   for tunnel links. Give each output its own virtual input-side edge, rather than reusing one port for all outputs.
   Register only Federation edges, not native membership evidence for the carrier Grid.
5. **Lifecycle.** Invalidate old authority immediately when an endpoint ceases to qualify, and coalesce reciprocal
   edge rebuilding on the server thread. Use the existing topology mutations so energy overlay invalidation and
   storage/crafting/processing refresh mechanisms see the disconnection. Avoid global per-tick part scans.
6. **Visual connectivity and diagnostics.** `router/CableVisualConnections` currently recognizes a closed list of
   block types from block states. A functioning part capability alone will not make the cable draw an arm into it.
   Add safe client-visible side information for a cable-bus part. The topology view should distinguish a P2P hop and
   explain inactive carrier power/channel/frequency state. Side-qualified identity must also survive UI graph IDs.
7. **Compatibility.** A new node discriminator affects every place constructing or serializing topology references;
   audit generated graph identifiers, part replacement, save reload and old block-node defaults. Do not claim this is
   just one new class or a tag-only change. No new AE2 Mixin appears necessary for the basic integration identified here,
   but P2PTunnelPart is an AE2 implementation class and both supported dependency bounds require verification.

## Cross-dimension and nesting boundaries

AE2 quantum bridges can connect a carrier ME network across dimensions; P2P frequency discovery itself is scoped by
Grid, not dimension. That does not make Federation business operations cross-dimensional automatically.
`FederationDomainRegistryAccess` keeps separate registries per ServerLevel. Provider target requests carry BlockPos and
are resolved against the current level; storage/crafting/claim/observability services likewise have level boundaries.

The earlier same-dimension-first recommendation is superseded by the user's explicit requirement: the first Federation
P2P release must support tunnel ends in different dimensions. The future coding agent must address cross-dimensional
registries, target addressing, returns and service lifecycles as part of that release. DESIGN's existing same-dimension
prototype restriction is superseded for this proposed feature's intended scope; DESIGN itself has not yet been edited.
Cross-dimensional support does not itself authorize forced chunk loading, whose product behavior remains undecided.

A non-ME P2P type normally lacks ME P2P's compressed-channel restriction and could potentially be carried through ME
P2P. This is worth a dedicated test, not a compatibility promise. Carrier power supplied solely via its own Federation
tunnel can create a cold-start dependency: the tunnel cannot establish connectivity until its carrier has power and
channels. Preserve native power requirements; document external startup power or a reserve instead of fabricating power.

## Qualification plan

No new feature exists to execute yet. A future prototype should run actual native P2P parts with:

- Attunement by cable and native memory-card input/output pairing, including converting a different P2P type.
- Storage, Processing input/return and energy-sharing operations through a tunnel while native Grids stay independent.
- One-to-many outputs, duplicate physical routes and cycles without duplicate inventory or capacity.
- Two different frequencies on different sides of the same cable bus, plus equal numeric frequencies on separate
  carrier Grids: each pair must remain isolated.
- Power/channel loss, input/output removal, frequency/type changes, Grid splits, chunk unload and reload: no stale
  resource access, unchanged stored rules, correct return ownership and restoration.
- Save/server restart with native frequency and role persistence.
- Real-client cable arms, frequency indicators and P2P outage diagnostics.
- Cross-dimensional pairing and actual Federation operations, including disconnection, remote chunk unload, return
  ownership and restoration; an optional ME-P2P-carried case as separate coverage. A cross-dimension rejection-only
  implementation cannot pass the required first-release scope.

Existing baseline commands, which do not yet test this proposed feature:

```sh
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:test
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:check
```

Future added tests must verify actual native pairing and domain isolation; compilation or a manually populated
Federation registry alone would not qualify a working P2P feature.

## Primary references

- [Official P2P player guide](https://guide.appliedenergistics.org/1.21/items-blocks-machines/p2p_tunnels)
- [Official quantum bridge guide](https://guide.appliedenergistics.org/1.21/items-blocks-machines/quantum_bridge)
- [Pinned attunement registry](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/features/P2PTunnelAttunement.java)
- [Pinned P2P base class](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/parts/p2p/P2PTunnelPart.java)
- [Pinned P2P service](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/me/service/P2PService.java)
- [Pinned part capability event](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/parts/RegisterPartCapabilitiesEvent.java)

## Follow-up: future Federation quantum bridge

Naming clarification from the user: "Federation quantum bridge" is only a temporary working name for this idea.
The final name, appearance, visual theme and physical structure are undecided. Do not treat the working name as a
product naming decision or infer that the device must resemble AE2's Quantum Network Bridge. Future discussion should
use "wireless / cross-dimensional Federation connection" for the capability, or explicitly mark the device name as
provisional. The temporary name is user-supplied discussion terminology, not a placeholder implementation or asset.

The user subsequently stated that cross-dimensional operation must be considered for the future, and that they are
considering a Federation version of a quantum bridge using wireless bridging. This confirms a future design direction,
not authorization to implement it now or agreement on a final device specification.

Architectural proposal for discussion:

- Give physical Federation cable, Federation P2P and a future dedicated wireless bridge a common way to publish and
  withdraw topology connections. Resource execution continues through the existing native business paths.
- Represent connection endpoints with dimension, position, and an optional part/port discriminator. Validate live
  endpoint instances or generations so a replacement device cannot inherit an old runtime connection accidentally.
- Separate the identity of a link from the transient Federation Domain it connects. Keep existing Policy keyed by
  persistent network identities; a wireless connection changes reachability rather than granting new permissions.
- A future world/server-wide connectivity coordinator could link dimension-local endpoint registries. Whether to
  centralize the entire domain registry or coordinate local registries remains an implementation decision. Native
  inventories, CPUs and block entities remain owned by their actual levels and native services.
- P2P derives availability from its carrier ME Grid and native pairing. A dedicated Federation wireless bridge would
  derive availability from its own pairing and operating conditions; it need not depend on an intervening ME carrier.
- Cross-dimensional topology alone is insufficient: storage, crafting, processing target resolution, claims, returns,
  energy overlay lifecycle and observation scopes must also be qualified across levels.
- Scope this discussion to dimensions in one Minecraft server/save. Cross-server networking has not been requested.

Unresolved product choices: paired versus one-to-many links, pairing item/interface, range, wireless bridge structure,
operating energy and startup behavior, dimension restrictions, management entrance behavior, and chunk-loading policy.
Do not infer chunk-loading permission from the request for a wireless bridge. The existing prototype's no-forced-load
behavior is a possible initial behavior, not a newly confirmed restriction on this future feature.

The earlier same-dimension-first recommendation was subsequently superseded: cross-dimensional Federation P2P is
required in its first release. The future dedicated wireless device remains a separate idea with an undecided name,
appearance and delivery schedule.

## Subsequent comparative research (2026-10-03)

The user requested a separate approximately 100-project P2P investigation, allowing overlap with a separate
policy sample. The [100-project P2P survey](federation-p2p-mod-survey-2026-10-03.md) adds direct native-addon
precedents, carrier/endpoint distinctions, management UX and explicitly bounded adjacent references.
Applied Mekanistics and Applied Botanics provide actual native P2P resource-mode examples; they do not prove the
Federation payload's bidirectional topology or required cross-dimensional lifecycle behavior.

Product-level suggestions are maintained in [the P2P idea](../../docs/ideas/federation-p2p.md). No architecture,
operating cost, visual design or implementation schedule is selected by the comparative research.
