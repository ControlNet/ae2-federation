# Federation P2P

Status: product direction and core behavior confirmed; implementation deferred.

Discussion date: 2026-10-02. Comparative research expanded: 2026-10-03. [Idea index](README.md).

## Intent

Add a Federation mode to AE2's native P2P system, allowing players to carry Federation cable connectivity through
a carrier ME network. Connected Federation networks retain their existing sharing and policy behavior.

## Confirmed requirements

1. A P2P connection is equivalent to directly joining its Federation sides with Federation cable. Federation
   connectivity is bidirectional; the P2P input/output roles do not impose one-way Federation access.
2. One input with multiple outputs joins all connected ends into the same Federation Domain, including
   output-to-output reachability, just as physical cable would.
3. The carrier ME network provides the tunnel function and does not automatically become a Federation member.
4. Power and channel requirements align with AE2's other native P2P tunnels. No exact cost or version-specific
   implementation parameter has been selected.
5. The first release of this feature must support connections across dimensions within the same server/save.
   The earlier same-dimension-first suggestion was superseded.
6. Concrete implementation details are deferred to the future coding agent.

The user confirmed this idea before moving on to custom policies. This is not an instruction to implement it now.

## Proposals and open questions

- Reusing native attunement and memory-card interactions is supported by the initial investigation, but the exact
  interaction has not been selected as a product requirement.
- Chunk-loading behavior and any new visual assets remain undecided. Cross-dimensional support does not by itself
  authorize forced chunk loading.
- The future coding agent must qualify actual cross-dimensional business operations and lifecycle behavior,
  rather than treating discovery of a remote tunnel as complete feature support.

## Player experience ideas from the comparative survey

The [100-project P2P survey](../../.omo/knowledges/federation-p2p-mod-survey-2026-10-03.md) examines native
P2P extensions, network links, multiplexed transport, endpoint proxies and configuration tools separately.
The following are proposals for discussion, not new requirements or implementation selections.

| Candidate | Player benefit | Relevant comparison |
|---|---|---|
| Recognizable Federation tunnel mode | Players can identify what the tunnel carries and select the appropriate mode through familiar AE2 interactions | Applied Mekanistics and Applied Botanics document Chemical and Mana P2P extensions; AE2 establishes native attunement conventions |
| A tunnel inspection view | Find every end of a connection, see input/output roles and navigate a large installation without searching every cable | Better P2P lists a carrier's tunnels, binds outputs and highlights matching endpoints |
| Names and visual identities | Distinguish several Federation connections sharing the same carrier by names, colors or resource icons | Better P2P, XNet and typed/colored conduit systems; exact naming and visual assets remain open |
| Explain the carrier and the carried connection | Show which ME network transports the tunnel and which Federation domain its ends join | AE2 P2P and other typed carriers; the carrier is already confirmed to remain outside Federation membership unless independently connected |
| Explain one-to-many reachability | Present all ends as belonging to the same Federation connection, including output-to-output reachability | Native P2P binding roles are familiar, but the approved Federation payload is bidirectional |
| Distinguish saved binding from live operation | Explain an unloaded destination, unavailable carrier, missing channel or disconnected Federation side without treating them as the same failure | Better P2P diagnostics, remote-network availability references and chunk-loading comparisons |
| Inspect the whole cross-dimensional connection | Show each endpoint's dimension/location and help players identify it when they visit that location | Address cards, endpoint lists and destination-highlighting tools in the survey |

These references suggest that much of the feature's usability comes from understanding connections after they
have been built. A saved frequency, an in-world highlight and a currently working link are different information.
The Better P2P manual explicitly describes an overlay that can become stale; Federation would need an honest
way to present the freshness of any similar display.

Resource routers often have priorities, one-way outputs, receiving limits or public/private resource pools.
Those mechanics do not change the confirmed cable-equivalent P2P behavior. Filtering belongs to Federation's
policy discussion, and the tunnel's ME carrier does not gain access merely by carrying a connection.

## Related idea: wireless Federation connection

The earlier wireless / cross-dimensional connection discussion now has a separate
[remote Federation connections page](remote-federation-connections.md). It retains the provisional naming and
open product questions. First-release cross-dimensional P2P does not require this separate idea to ship alongside it.

## Research

- [100-project P2P comparison](../../.omo/knowledges/federation-p2p-mod-survey-2026-10-03.md): per-project
  mechanisms, relevance, popularity evidence, version boundaries and primary sources. This is a surveyed sample,
  not a claim that 100 mods implement native AE2 P2P modes.
- [P2P feasibility investigation](../../.omo/knowledges/federation-p2p-feasibility-2026-10-02.md): pinned AE2 API
  inspection, current project integration issues, lifecycle concerns and possible validation scenarios.
- [AE2 P2P guide](https://guide.appliedenergistics.org/1.21/items-blocks-machines/p2p_tunnels).

The investigation is evidence and implementation guidance, not approval of a particular architecture. Its initial
same-dimension feasibility finding is not proof that the required cross-dimensional feature is implemented.
