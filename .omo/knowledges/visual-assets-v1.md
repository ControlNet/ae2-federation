# Visual assets v1 (2026-09-26)

- Source of truth: `tools/visual/build_assets.py`, adapted from website v07; direction from visual spec v0.2.
  Output: `common/src/main/resources/assets/ae2federation`. Approved environment: `tools/visual/.pixi/envs/default`.
- MC 1.21.1 / NeoForge 21.1.250 / AE2 19.2.17 retained. Pinned source confirms `CompositeModel` and `ExtraFaceData`.
- Provider has six native `facing` states. Assets authored SOUTH need y=180/270/0/90 for N/E/S/W and x=90/270 for U/D.
  Do not UV-lock: all peripheral cyan ends must follow the true Federation face. Items are separately north-oriented.
- Endpoint is fixed EAST (`EndpointBlockEntity.FEDERATION_FACE`), does NOT register FederationPortCapability. It must
  not grow a Federation Cable connection or acquire rotation just for its cyan front.
- AE2 `QuadRotator` uses NORTH geometry; `BusCollisionHelper` uses local SOUTH boxes. Bridge's source SOUTH volume
  [4,4,10]..[12,12,16] becomes NORTH [4,4,0]..[12,12,6]. Contact seats are 6x6; middle is 8x8.
- Cable visual mask reads blockstates from the chunk snapshot: Cable/Router all faces; Provider facing only. This is
  a read-only projection of current port registration, no network mutation or extra packet. If the registration rules
  expand, update the projection. The model wrapper and immutable ModelData cache are installed client-side only.
- Vanilla `BaseEntityBlock` defaults to invisible rendering: Router, Cable and Endpoint explicitly return MODEL.
  Cable must use noOcclusion or it hides neighboring block faces despite its narrow geometry.
- Minecraft rejects elements with zero faces. Fully enclosed six-way hub interiors must be omitted; validate the
  connected enclosed volume AND the exact exposed surface, rather than retaining an empty-faced element.
- Revised flow has a continuous 4x4 body with opaque animated base and a single band; no old pair of 1x1 streams.
  Unit-face union checks validate every glass/flow surface and all 64 masks. Collars are stationary half-rings at block
  boundaries (16 model units, not 16 game blocks).
- No model datagen currently exists. Regeneration validation compares every output byte. Website preview/caps and ZIPs
  are not in the JAR. Final runtime, screenshots and limitations are in `docs/art/acceptance.md`.
- Use an isolated world and installed production JAR. `tools/visual/build_scene.py` makes a datapack with 64 actual
  cable clusters and a 2048-cable/128-device stress scene. Datapack functions cannot use permission-level-4 save-all;
  issue save-all/stop through the dedicated console instead.
- Wait for client AND server process exit before copying replacement JARs. One rejected intermediate run replaced a
  JAR during shutdown and failed a late class load; the next launch then hit the retained world lock. This was test
  orchestration misuse, not evidence of a mod business defect. Subsequent clean shutdown/reload was rechecked.
- Vanilla `tp ... facing` defaults to feet anchoring; use explicit yaw/pitch with eye height for reproducible cameras.
  F2 must wait for equip/teleport settling; immediate capture can show a lowered item during its equip animation.

## Final result

- JAR SHA-256: `08c057e772619bd51589670c1f4d83a58708a8feb75262deb95e1d94bee1ed84`. Both installed copies match.
- Build + archive checks pass; 236 JUnit tests pass. Structural validator: 266 models / 5088 faces / 64 masks.
- Final production client and server exit 0; final logs have no ERROR or missing-model messages. Runtime receipt is `build/visual-v1/evidence/runtime-summary.json`.
- Actual wrench cycle verified all six Provider facings; representative mouse placement verified DOWN. Clicking the wrench along the current facing axis does not change facing and may open the native UI; rotate from a perpendicular face.
- Final F3+L software-GL samples: gallery mean/p95 21.09/25.58 ms; 2048 cables + 128 machines 51.73/60.53 ms, with zero queued uploads/batches in both. No hardware GPU regression claim.
