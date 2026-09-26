# Task 12 Hub And Federation Cable Topology

## Production boundary

`HubBlockEntity` owns one `HubFacePort` for every `Direction`. Each face has a separate AE2 managed node configured as
an in-world, zero-idle, `CANNOT_CARRY` node exposed only on that face. The Hub registers
`AECapabilities.IN_WORLD_GRID_NODE_HOST`, allowing real adjacent native cables and devices to discover the correct face
node. Production never calls `GridHelper.createConnection`, so the Hub does not create cross-face native Grid edges.

Federation Cable exposes only the sided `FederationPortCapability.BLOCK` capability. Hub and cable ports form a custom
physical edge when their immutable positions are adjacent and their faces point at one another. This capability has no
native AE2 node, energy service, transfer buffer, registry, routing, or Policy behavior.

## Classification and lifecycle

Each face independently resolves either a `HubPortBinding.Native`, `HubPortBinding.Federation`, or the singleton
disconnected binding. Native qualification delegates to `NativeAttachmentResolver`; Federation qualification comes from
one `BlockCapabilityCache` bound to the exact adjacent position and opposite face. Zero or two valid candidates fail
closed.

Neighbor, native-node, and Federation capability invalidations clear only the affected face binding immediately and mark
that face dirty. The server ticker re-resolves only dirty faces. Resolution checks `ServerLevel.isLoaded` before native
capability access, while the NeoForge cache itself returns null for unloaded positions. Managed nodes and their six unique
NBT tags are destroyed with block-entity unload/removal.

## Verified QA contract

The exact cases are `hub.mixed-six-faces`, `hub.six-independent-me`, `hub.repeat-network`, `hub.port-replacement`, and
`hub.reject-unsupported`. They prove real native cable/device acceptance, mixed and all-Federation custom layouts, six
isolated native Grids, repeated-Grid deduplication with face ownership, affected-face replacement, unsupported rejection,
zero native joins, and zero port idle power.

Each case emits one zero-transfer topology operation plus deterministic `AE2F_HUB_NATIVE_TRACE` facts. The shared
schema-v3 producer/consumer requires the exact case set, child exits, artifact/log cardinality, case identity, trace
correlation, and case semantics. `federationTaskTwelveEvidenceSelfTest` fully rebinds copied evidence and rejects
fabricated success, rebound case identity, incomplete trace, and incomplete attempt evidence.

Canonical evidence is `.omo/evidence/task-12/attempt-20260914T155900227Z/result.json`.
