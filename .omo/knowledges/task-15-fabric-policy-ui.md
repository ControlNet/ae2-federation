# Task 15 Fabric Policy UI

## Production architecture

- `FabricPolicyMenu` is the single `PlayerUIMenuType` used by Hub and Multipart Bridge right-click entrypoints.
- `FabricPolicySession` lives only on the logical server. It freezes a `FabricReference`, sorted member selection, and expected `PolicyRevision` when opened.
- A mutation validates player identity, loaded object, eight-block distance, current Fabric generation, bounded directional selection, and Policy CAS revision before calling `PolicyService.edit`.
- Disabled Bridges expose their `BridgeOperationalReason` but cannot submit edits. A stale generation or revision produces a synchronized visible rejection and disables further submission.
- Menu closure releases only menu bindings. Policy sharing remains world-owned by `PolicyService`.

## LDLib2 resource rule

LDLib2 2.2.34 indexes external stylesheets only below `assets/<namespace>/lss/`. XML may live below `ui/`, but a referenced stylesheet stored beside it under `ui/` is silently absent. The production pair is therefore:

- `assets/ae2federation/ui/fabric.xml`
- `assets/ae2federation/lss/fabric.lss`

Use supported LSS texture functions such as `border(...)`; do not assume Java sprite expressions such as `built-in(...)` are valid LSS.

## Evidence contract

- Task 15 uses exactly `ui.hub`, `ui.bridge`, `ui.shared-policy`, `ui.stale-context`, and `ui.close-unsubscribe` in an actual Minecraft client under Xvfb with synthetic input.
- `TaskFifteenWorldFixture` arranges real AE2 Hub/Multipart Bridge topology only. Each scenario opens the production `FabricPolicyMenu`, derives its directional key from current settled `NetworkId` values, and observes authoritative `PolicyService` and Fabric state; there is no shadow UI state service.
- Build the Bridge from its validated core first, wait for both native identities to settle, then extend toward the Hub. Adjacent extensions must use different AE2 cable colors (red and blue in the canonical fixture) so the main and outer networks cannot cross-join.
- Persisted evidence binds all five outcomes to one directional record identity and the run correlation ID.
- `forged-task15-ack` and `stale-context-success` rebind both inner and outer reports and must still be rejected by semantic verification.
- The required QA command is:

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:federationUiTest -Pcases=ui.hub,ui.bridge,ui.shared-policy,ui.stale-context,ui.close-unsubscribe -PevidenceDir=.omo/evidence/task-15
```

The current source-bound evidence is in `attempt-20260914T205305294Z`; all five scenarios passed with six 1280x720 screenshots from this fresh attempt, including separate accepted and disabled Bridge states.
