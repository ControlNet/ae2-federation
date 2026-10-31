# Multiblock Routers and Switches

Status: implemented and merged into `dev` on 2026-10-11 (`c8e1a4d0`); the artist's connected face tiles followed the
same day (`1dd81c1d`). The former idea is preserved as an
[archived discussion](../archive/ideas/multiblock-device-assembly-and-appearance.md).

## Behavior

- **Forming.** Same-type blocks that share a face are judged together, by AE2's crafting-CPU rules. They form one
  device when they fill a solid box with every edge at most 8 blocks (`DeviceStructure.MAX_EDGE`); 1x1x1 is a device
  too. Blocks that meet only at an edge or a corner stay separate, and a Router and a Switch never form one device.
  ME Federation Beam Converters form by the same rules plus an exact size and one facing
  ([operating power](operating-power.md)).
- **Invalid groups.** A hollow, L- or T-shaped, protruding or oversized group, or a finished box with one more block
  of its type against it, forms nothing: all its faces disconnect and its networks leave the domain. No smaller part
  forms on its own. Rules, processing ownership and configuration are untouched.
- **Faces.** A formed device keeps every outer block face as a port. A Switch face still attaches the ME network it
  touches, and the networks on one Switch never join each other; one network on several faces counts once. A Router
  face links Federation ports only. Faces between members link the members through Federation.
- **Capacity.** A formed Router counts as one Router, so it ends each cable segment once (see
  [cable capacity](cable-capacity.md)). A Switch of any size does not end a segment.
- **Feedback.** Right-clicking a formed member opens the domain screen; right-clicking an unformed member with an empty
  hand shows the reason on the action bar (`ae2federation.structure.*`); with anything in hand the click passes to the
  item (`SKIP_DEFAULT_BLOCK_INTERACTION`), so a held block is placed on it. Jade shows "Structure: WxHxD" or the reason it is not
  formed.
- **Wrench.** Shift+right-click with a wrench (`c:tools/wrench`) puts one block straight into the inventory, as AE2's
  `WrenchHook` does for AE2 block entities (Provider and Endpoint go through AE2's own hook). `WrenchDismantle` covers
  every `FormableDevice` (Router, Switch, Beam Converter, Beam Former, Quantum Cube); `BundleInteraction` routes
  Federation Cables, bundled or not, to it. One member of a body comes out and the rest re-forms or unforms as on a
  break; break particles are skipped and AE2's frame-removal sound plays. GameTest `wrenchdismantleseveryfederationblock`
  (2026-10-31).
- **Unloaded chunks.** A body across unloaded chunks keeps its last validated state and is checked again when its
  chunks load; chunks are never force-loaded.
- **Appearance.** The artist's connected tiles (`router_*`, `switch_*`) draw one continuous outer frame per body;
  repeated cores and panels keep their scale.

## Evidence

- Unit tests `DeviceStructureTest`, the Java/Python geometry parity file
  `common/src/test/resources/multiblock/device-structure-parity.json`, and the GameTests listed in
  [the runtime notes](../../.omo/knowledges/multiblock-runtime-integration-2026-10-11.md).
- Dedicated server with a remote client (2026-10-14): a 2x2x1 Switch with three networks, two 1x2x2 Routers, a 2x2
  bundle formed by real wrench clicks between them, and an L of three Routers. Formed shapes, Jade lines and the
  right-click reason showed the same when placed live, after the client left and reloaded the chunks, and after a
  server restart. Notes: [dedicated-server check](../../.omo/knowledges/dedicated-server-sync-2026-10-14.md).
- Guide: the Router and Switch item pages ("Bigger Routers", "Bigger Switches").
- Shader packs (2026-10-14): Complementary Reimagined, BSL and MakeUp under Iris 1.8.12 with Sodium 0.6.13, and
  Complementary under Iris 1.8.14-beta.1 with Sodium 0.8.13, drew the formed bodies, bundles and selection box as
  without shaders ([notes](../../.omo/knowledges/iris-shader-compatibility-2026-10-14.md)).
- Not verified: hardware GPU lighting (screenshots use llvmpipe), resource packs replacing the tiles, and performance
  with many large bodies.
