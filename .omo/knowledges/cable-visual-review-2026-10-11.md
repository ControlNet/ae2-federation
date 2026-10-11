# Cable visual review (2026-10-11)

Review scope: actual Blender `cable-detail.png` and `flow-study.png`, current
`CableFlowGeometry` and renderer behavior. These are proposals, not approved artwork
changes or claims of Minecraft visual/performance acceptance.

## Findings

- The corrected low-angle view exposes a continuous cyan beam, but the core and halo
  have weak visual separation. Rectangular moving bright bands dominate the local contrast.
- The white/dark shell rails dominate high-angle views. Real opaque geometry hides the
  beam at some angles; changing emission cannot remove that occlusion.
- Production straight flow uses two perpendicular quads across a 4/16-block envelope.
  Texture alpha controls apparent thickness. This is a useful lightweight baseline.
- BEND has static U=0.03125 in the renderer; HUB has static local UVs. Animated FLOW
  arms fade to zero at the inner endpoint. Thus visual pulse continuity through bends
  and junctions is not currently implemented; this follows from code, not a motion
  judgment inferred from a still image.
- Current phase implies a 4-block repeat, 3.2-second texture cycle and 1.25-block/second
  phase propagation on straight positive-axis runs at 20 TPS. This is decorative flow,
  not observed traffic or a route through the network.

## Suggested iteration order

1. First compare a texture-only sharper-core candidate against the baseline. Keep the
   existing geometry and shell. Trial apparent core width 0.75–1.25 model units inside
   a quieter 2–3-unit halo; pale cyan core, saturated cyan shoulder, transparent margin.
   Trial core alpha 0.65–0.85 and halo 0.12–0.20, subject to the shader's pre-vertex-alpha
   0.1 discard. These are starting hypotheses, not validated settings.
2. Shape pulse heads/tails instead of square bright slabs; keep a persistent core so
   motion does not look like repeated breaks. Compare current 1.25-block/s motion to
   0.6–0.9-block/s candidates using actual animation, not single stills. Preserve
   boundary phase and clock-wrap continuity when changing the formula.
3. Improve local elbow UV continuity and junction brightness separately. A moving pulse
   through a turn requires explicit arc-length/phase design; simply animating BEND's
   constant U does not guarantee seam continuity with both arms. Keep branching effects
   decorative unless real telemetry is explicitly introduced.

First retain the artist's shell. If ordinary gameplay angles still hide too much,
compare restrained inner-rail/window changes as a separate design decision. Do not
fake visibility by disabling depth testing or turning the original opaque frame clear.

Compare candidates under identical camera/light/time settings: straight, elbow, T and
six-way, machine endpoint, negative-coordinate seam, near/far and side/high-angle views.
Blender is for design comparison; final transparency, coplanar layers and cost require MC.
