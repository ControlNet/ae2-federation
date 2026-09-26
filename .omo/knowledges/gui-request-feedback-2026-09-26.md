# Request waiting and rejection feedback

## Implementation

The owned mutation payload now includes a per-request UUID. A server-to-client reply echoes container ID, menu nonce,
request UUID, request sequence and dispatch result. Action and reply payload registration use protocol version 2;
client and server must use matching builds. Existing server-side container/nonce/sequence/domain/revision validation
and mutation authority remain unchanged. The reply describes dispatch acceptance, not business-operation success.

`ActionRequestProgress` gates one local request at a time. Accepted dispatch only finishes waiting when both its
matching reply and a newer authoritative menu sequence have arrived, in either order. Rejection finishes waiting
without requiring a sequence increment. A reply for another request UUID or sequence cannot finish a pending retry.
Client reply delivery also checks the active menu/container and the server-issued menu nonce.

While pending, editing controls are inactive and a localized waiting message replaces the normal footer. Rejections
show a localized reason. Accepted mapping business refusals still use the existing mapping feedback. No elapsed-time
timeout is treated as confirmation. Navigation away remains available; replies to a different menu are ignored.

## Tests and evidence

- Three pure Java tests cover duplicate submission, reply-before-state, state-before-reply, unrelated replies,
  rejection without sequence advance, and an old reply arriving during a same-sequence retry.
- The actual-client mapping scenario sends two real clicks in one frame, checks immediate waiting text and the
  disabled button, then verifies the authoritative mapping remains installed (two toggles would remove it).
- A genuine external Storage policy edit invalidates the open menu. A subsequent mapping request shows the
  translated stale-revision reason and leaves the released endpoint unclaimed.
- The first runtime test checked computed visibility before LDLib2's next layout pass. Its error screenshot already
  showed the actual waiting footer. The test now checks immediate text/control state, preserving the one-frame
  duplicate-click exercise; it does not force or fabricate server latency.
- The executable packet probe verifies the new request UUID and complete reply round trip in addition to the prior
  malformed/context/observation/meter boundaries. This probe is not the full dedicated-server release qualification.

Final UI evidence: `.omo/evidence/gui-request-feedback/attempt-20260926T114425158Z`, `BUILD SUCCESSFUL`, 6/6 scenarios,
770 steps, 64/64 checks. Unit XML totals: 244 tests, zero failures/errors/skips.
Packet probe: `.omo/evidence/gui-request-packets/facts.properties`, every reported fact true, `BUILD SUCCESSFUL`.
Rejection screenshot: `ldlib2/screenshots/ui.mapping/328_ui-request-rejected.png`.

```sh
./gradlew :neoforge-1.21.1:federationUiTest \
  -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict \
  -PevidenceDir=.omo/evidence/gui-request-feedback \
  --dependency-verification=strict --no-configuration-cache --console=plain

./gradlew :neoforge-1.21.1:federationTaskThirtyFivePacketProbe \
  -PfederationPacketEvidenceFile=.omo/evidence/gui-request-packets/facts.properties \
  --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: both builds succeed; all UI cases/checks and packet facts pass. Do not edit source during the UI wrapper.
The complete original-screen-direction audit remains open; this change does not establish full-goal completion.
