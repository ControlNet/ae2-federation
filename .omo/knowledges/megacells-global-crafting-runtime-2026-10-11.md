# Actual MEGACells global crafting extension (2026-10-11)

Scope: compatibility coverage only, no production edits or class-name allowlist. Actual
pinned artifact is MEGACells 4.11.0. `DecompressionService` implements `ICraftingProvider`
and registers itself with official `addGlobalCraftingProvider` in its constructor. It has
no corresponding node crafting-provider service. Installed Decompression Module parts
activate its scan of native chest/drive Bulk Cells. Compression-enabled partitioned cells
generate real `DecompressionPattern` objects; no encoded pattern, Pattern Provider or
Molecular Assembler is needed for this extension.

`MegaGlobalCraftingScene` places the real module and a real Bulk Cell, configures its
ordinary cell-workbench inventory for iron blocks and installs its actual Compression
Card through the native upgrade inventory. It enables only Crafting, puts one iron block
in the consumer, and never enables Storage. The source's native crafting service must
advertise iron ingots, while no source node's crafting provider publishes that output.
The consumer runs an actual AE2 crafting calculation and must select exactly one real
addon decompression recipe and exactly one projected provider, consuming one iron block.
The native consumer CPU submits the plan; the real addon queues and inserts nine outputs,
and Federation must return all nine and settle debt with none left in factory stock.
The test finally removes the actual module through its native part host and requires the
remote generated pattern to disappear.

This is real addon execution, not a fake global provider or a hand-operated machine. The
fixture and assertions live in a separate compatibility test mod, so this does not claim
production-client/no-testmod evidence or coverage of every possible third-party provider.
No third-party node/global registration is replayed. Exact class assertions are confined
to the test's expected native recipe type, not production discovery.

```bash
./gradlew --dependency-verification=strict :neoforge-1.21.1:jar :neoforge-1.21.1:compatTestJar
python3 tools/compat_run.py --accept-eula --no-build --tests megaglobaldecompressioncrafting megacells
```

Expected: the named compatibility GameTest passes with actual plan, execution, return,
conservation and withdrawal assertions. Production code is unchanged; this qualifies a
real ecosystem extension path already repaired by the earlier generic global-provider
registration hook. It is not evidence that this particular addon was previously broken
in every configuration.

The first run placed the real module on the fixture cable's UP face, which the shared
fixture itself uses to query an exposed native node. The module correctly occupied that
face and the fixture's later Grid lookup failed. Moving the module to a free WEST face
preserves every assertion and timeout. This fixture correction is not a production defect.

Runtime result: corrected scene **1/1 PASS (20 seconds)** against the real pinned 4.11.0
jar. Planning, one-provider registration identity, actual module execution, Crafting-only
return, quantity conservation and post-removal remote withdrawal all passed. Strict
compatibility compilation and staged secret scan passed. Receipts are retained under
`.omo/evidence/megacells-global-crafting-2026-10-11/` (ignored runtime artifacts).
