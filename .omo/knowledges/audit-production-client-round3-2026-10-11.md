# Final production client return verification, third audit

Production source `dc8b5b42a2132b11205bd8c9faabfc7046fe857a` was built in the clean third-round integration worktree,
separate from ongoing user artwork edits. Strict `check build compatTestJar` passed. The installed server and client
production jar SHA-256 is `39caefa772950eb8163f1b73b1a719070fc91ecf6f8e643112a2d3e4d510d12c` on both sides.
Their only four mod jars are Federation 0.0.6, AE2 19.2.17, GuideME 21.1.1 and LDLib2 2.2.34. Neither loads testmod.

The isolated `build/audit-round3-production` runtime copies the earlier acceptance world. Its native structure was
originally exported by test tooling, and operator chunk tickets keep the scene available. All new runtime orders use
the actual terminal, native CPU and Pattern Provider, real furnace, hoppers and Import Bus. Real XTEST client input
submits the order; console commands only supply named fixture ingredients, block/unblock collection, inspect and save.

1. Verified the saved Policy contains exactly one CRAFTING/REQUEST entry, revision/highWatermark one, and no Storage
   entry. The terminal begins with seventeen stone from the earlier completed runs.
2. Gave the player exactly two cobblestone and shift-clicked them into the terminal. Middle-clicked craftable stone,
   selected two, viewed the native plan and pressed Start. No inventory duplication or replacement execution occurs.
3. Redstone holds the output hopper closed. The furnace consumes both ingredients and contains exactly two stone;
   native CPU status waits for two, and diagnostics report owed=2, held=0. Saved the game and stopped the first JVM.
4. A second server JVM opens the same world. Furnace still contains two stone, native CPU still waits for two, and
   owed=2/held=0 survive additional sweeps. Removed redstone. The original job completes and terminal stock becomes
   nineteen. SavedData now contains no owed entries, jobs, unknown CPU markers or transit.
5. Saved/stopped and started a third JVM. Rejoined the actual client: nineteen stone remain. Saved Policy parses
   exactly equal to the original one-entry object; final ledger is empty. Client and all server processes exit zero.

This is actual production-client and process-restart evidence on the stated artifact, not a GameTest assertion or
SavedData round-trip. It does not substitute for separate fault-injected partial-insertion, delayed-container or
addon CPU scenarios. No additional large-factory claim is made. Raw screenshots, parsed native NBT, installed-mod
hashes and logs are retained at `.omo/evidence/audit-round3-2026-10-11/` in the main workspace. The temporary Xvfb
process was stopped after the client exited; the saved acceptance world and evidence remain available.
