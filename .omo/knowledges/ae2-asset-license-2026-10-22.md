# AE2 asset licence (2026-10-22)

Source: the License section of the upstream AE2 README (the jar's `neoforge.mods.toml` only says "See GitHub
repository for details"; its `NOTICE` covers bundled libraries, not art).

- AE2 API: MIT. AE2 mod code: LGPLv3.
- Textures and models: CC BY-NC-SA 3.0, © 2020 Ridanisaurus Rid, © 2013–2020 AlgorithmX2 et al.
- Text and translations: CC0. Guidebook click sound: CC BY 4.0 (EminYILDIRIM, freesound).

Consequence for this repo: any texture we ship that is recoloured or otherwise adapted from AE2 art must carry
attribution, stay non-commercial and be shared under CC BY-NC-SA 3.0 (share-alike), so it cannot be All Rights
Reserved. Referencing AE2's own resources at run time (`ae2:` namespace) ships no copy and is unaffected.
`README.md` License section (8f15f861) now says the in-development placeholders recoloured from AE2 are
CC BY-NC-SA 3.0; the rest of the art stays All Rights Reserved. Replacing a placeholder with original art removes it
from that exception.

## Audit of v0.0.7 (released 2026-10-15) against AE2 19.2.18 textures

Method: per-texture alpha-mask and neighbour-luminance-order match against every AE2 texture of the same width, then
a side-by-side contact sheet. AE2-derived art already shipped in v0.0.7 (not only placeholders):

- `block/pattern_provider{,_side,_back}.png`, `block/processing_endpoint{,_side,_back}.png`: AE2 pattern provider
  frame recoloured purple (`_back` matches `pattern_provider_alternate` pixel for pixel in shape).
- `item/nexus_processor.png` = AE2 `calculation_processor` recoloured; `item/nexus_processor_press.png` almost
  identical to AE2 `calculation_processor_press`; `item/printed_nexus_circuit.png` = `printed_calculation_processor`
  recoloured; `item/nexus_core.png` close to `formation_core`.
- `part/p2p_tunnel_federation.png`: same concentric-square layout as AE2 `p2p_tunnel_redstone`, teal.

Router/Switch/cable textures scored low and look original. The Beam Converter (the old "Beam Charger", added
2026-10-17 in e9a996cc, after v0.0.7) uses `tools/visual/build_converter_placeholder.py`, which recolours the
Federation Pattern Provider textures above, so it inherits the AE2 derivation; the Beam Former and Quantum Cube
placeholders come from the same script.

## How ExtendedAE handles it (checked 2026-10-22)

ExtendedAE (GlodBlock) declares only LGPL-3.0 (`LICENSE.txt`, `license="LGPL-3.0"` in the 2.2.39 jar); there is no
separate asset licence and no CC BY-NC-SA notice. Its mods.toml credits "Ridanisaurus for the new textures", and the
GitHub history shows Ridanisaurus, the holder of AE2's 2020 texture copyright, committing "Textures Update"
(2024-06-30, 109 PNGs) and "GUI Update" (2024-07-05, 24 PNGs). So most of its AE2-style art was redrawn by AE2's own
texture artist; textures added since by GlodBlock and others carry no explicit licence statement.

## How Advanced AE handles it (checked 2026-10-22)

Advanced AE (pedroksl) declares only LGPL-3.0 (`LICENSE.md`, `license="LGPL-3.0"` in the 1.6.12 jar), with no asset
licence or CC BY-NC-SA notice. The jar credits "Ridanisaurus Rid for texture style, Jm³ and Sea_Kerman for models";
the README thanks Jm³ for the base models and textures. Texture commits are by the author (78) and one contributor,
none by Ridanisaurus: AE2's artist is credited for the style, not as author, and no permission is documented.

## Survey of the AE2 addons in the compatibility runs (2026-10-22)

31 addon jars under `neoforge-1.21.1/build/compat/runs/*/mods` (latest version of each, AE2 as a hard dependency).
Metadata from `META-INF/neoforge.mods.toml` and bundled licence files; textures scored against AE2 19.2.18 (HLS
lightness order + alpha mask, 16-64 px wide, trivial textures skipped; >=0.95 counted as a likely recolour).

- None of them ships a CC BY-NC-SA notice for AE2-derived art. Licences are LGPL-3.0 (most), MIT, GPL, CC0 or All
  Rights Reserved, applied to the whole jar.
- Likely AE2 recolours (count >= 0.95): AdvancedAE 20, ae2lt 33, AppliedFlux 23, Applied Mekanistics 6, ArsEng 7,
  Data Energistics 30 (+3 exact), ExpandedAE 20, ExtendedAE 28, MEGA Cells 32, NeoECO 19 (+1 exact), NeoECO
  Prototype 15 (+5 exact), OmniSequence 4, AE2 Pattern Disk 3 (+4 exact); a few single hits elsewhere.
- Credits only: ExtendedAE and AppliedFlux credit "Ridanisaurus for the new textures"; AdvancedAE credits him for
  "texture style"; MEGA Cells thanks the AE2 dev team.
- NeoECO puts AE2 GUI textures under `textures/gui/ae2/` with `LICENSE-AE2.txt`, which is the LGPL-3.0 text (AE2's
  code licence, not its CC BY-NC-SA art licence).
- Data Energistics: "AGPL-3.0-or-later (code); All Rights Reserved (resources/assets)" with `LICENSE.RESOURCE`, which
  says third-party resources keep their original licence, yet names no AE2 file.
- aeallpattern `META-INF/NOTICE.md` lists every asset adapted from AE2 Lightning Tech with source revision and
  changes: the most careful notice seen, though it covers another addon's art, not AE2's.

## Nexus item icons, pixel by pixel (dev = v0.0.7, unchanged)

| ours | AE2 | silhouette pixels differing | identical-colour pixels |
|---|---|---|---|
| `nexus_processor` | `calculation_processor` | 0 / 256 | 69% (only the accent pixels recoloured) |
| `nexus_processor_press` | `calculation_processor_press` | 0 / 256 | 95% (8 pixels changed) |
| `printed_nexus_circuit` | `printed_calculation_processor` | 0 / 256 | 0% (whole palette shifted, same layout) |
| `nexus_core` | `formation_core` | 36 / 256 (side lugs reshaped) | 64% |

Controls against `logic_processor` / `logic_processor_press` give 33-40 differing silhouette pixels and 13-34%
identical colours, so the match is to those specific files, not to AE2's general style. Diff sheet:
`build/ae2-license-check/nexus-items-vs-ae2.png` (ours | AE2 | diff: grey identical, red recoloured, yellow shape).

## Full classification of the shipped art on dev (2026-10-22)

Scope: every non-code resource the jar ships (56 textures, 26 models, 4 guide images, logo, font). Textures scored
against AE2 19.2.18 and against our own textures (silhouette, identical-colour share, lightness order), then checked
by eye. Overview sheet: `build/ae2-license-check/classification.png`; diff sheets `blocks-a/b/c.png`,
`nexus-items-vs-ae2.png` in the same folder.

**A. Recolour of one AE2 texture, same pixel layout (6) -> CC BY-NC-SA 3.0**
`block/pattern_provider_back` (AE2 `pattern_provider_alternate`), `block/pattern_provider_side`
(`pattern_provider_alternate_arrow`), `item/nexus_processor` (`calculation_processor`), `item/nexus_processor_press`
(`calculation_processor_press`), `item/printed_nexus_circuit` (`printed_calculation_processor`),
`part/p2p_tunnel_federation` (AE2's P2P type panels, e.g. `p2p_tunnel_energy`; concentric squares, little to protect).

**B. Partly AE2: AE2's machine frame or outline pixels kept, new centre (14) -> CC BY-NC-SA 3.0 unless the artist
says otherwise**
`block/pattern_provider`, `block/processing_endpoint{,_side,_back}` (frame of AE2 `pattern_provider*`),
`block/router/router{,_corner,_side_hor,_side_ver}`, `block/switch/switch{,_corner,_side_hor,_side_ver}` (frame of
AE2 `pattern_provider_alternate` / `generics/top`, strips as in `spatial_pylon/base_spanned`), `item/nexus_core`
(`formation_core`, 64% identical pixels, lugs reshaped), `part/bridge_sides` (`import_bus_sides` corners and layout).

**C. Our placeholder recolours of A/B, `tools/visual/build_converter_placeholder.py` (13) -> CC BY-NC-SA 3.0**
`block/beam_converter/*` (3), `block/creative_beam_converter/*` (3), `block/beam_former/*` (3) from the Pattern
Provider faces; `block/quantum_cube/quantum_cube{,_corner,_side_hor,_side_ver}` from the Router's.

**D. Original, no AE2 pixel source found (23) -> All Rights Reserved**
`block/armor`, `block/collar`, `block/glass`, `block/stream_u`, `block/stream_v`, `block/large_cable/large_cable`,
`block/router/router_base`, `block/switch/switch_base`, `block/quantum_cube/quantum_cube_base` (recolour of
`router_base`), `entity/cable_flow`, `misc/bundle_selection_hatch`, `part/bridge`, and the 11 `part/cable/dense/*`
(collar, connector, core, core_connected_1/2/2_straight/3/4, line, stream_u, stream_v). The dense cable frames share
AE2's frame style but no colours or pixels.

**Not textures**
- Models (26 JSON): none copies AE2 geometry (0-2 coincident boxes out of 22-73). `item/federation_p2p_tunnel` and
  `part/p2p_tunnel_federation` take AE2's P2P base model as `parent` at run time; nothing is copied. Dense cable
  geometry lives in Java and follows AE2's code (LGPL-3.0, compatible with our AGPL), not its art.
- `font/canvas_cjk.ttf`: Droid Sans Fallback subset, Apache-2.0, licence and changes in `canvas_cjk_license.txt`.
- Rendered images: `ae2guide/{,_zh_cn/}assets/teaser.png` is an in-game render showing AE2 blocks and vanilla
  chests; `statistics.png` is our Statistics screen with vanilla item icons; `ae2federation_logo.png` renders the
  Router, whose faces are group B. Screenshots of the game are the usual practice; name AE2 and Mojang as the
  owners of what they show.
- GUI sprites and other `ae2:` textures are referenced at run time and not shipped.
- Off dev: `feature/function-system` ships the Neo ECO ECO-C screen atlas (GPL-3.0, own licence file, 89376e7b);
  add it to the notices when that branch merges.

Open: the artist should confirm how groups A/B were made (the Nexus press commit says it replaced AE2's texture, yet
it matches AE2's `calculation_processor_press` at 95%).

## Classification by game object (dev, 2026-10-22)

Texture use traced through models/blockstates and the code that binds textures (`CableBakedModel`,
`DeviceStructureBakedModel`/`DeviceStructureGeometry` for formed Router/Switch/Quantum Cube faces, `BundleOverlay`,
`FederationP2PTunnelPart`, `MultipartBridgePart`).

| Object | Textures (group) | Object contains |
|---|---|---|
| ME Federation Pattern Provider | front `pattern_provider` (B), side `_side` (A), back `_back` (A) | AE2-derived |
| ME Federation Processing Endpoint | front, `_side`, `_back` (all B) | AE2-derived |
| ME Federation Router | standalone `router` (B); formed: `router_base` (D) + `_corner`/`_side_hor`/`_side_ver` (B) | AE2-derived |
| ME Federation Switch | same layout as the Router (`switch_base` D, rest B) | AE2-derived |
| ME Federation Quantum Cube | standalone `quantum_cube` (C); formed: `_base` (D) + `_corner`/`_side_*` (C) | AE2-derived (placeholder) |
| ME Federation Beam Converter / Creative / Beam Former | front, side, back each (C) | AE2-derived (placeholder) |
| ME Federation Bridge (part + item) | front `part/bridge` (D), sides `part/bridge_sides` (B) | AE2-derived (sides) |
| Federation P2P Tunnel | panel `part/p2p_tunnel_federation` (A); body is AE2's own P2P base model, referenced | AE2-derived (panel) |
| Nexus Processor / Inscriber Nexus Press / Printed Nexus Circuit | one icon each (A) | AE2-derived |
| Nexus Core | `item/nexus_core` (B) | AE2-derived |
| ME Federation Cable, 2x2/3x3 bundles | `part/cable/dense/core*` (6), `line`, `collar`, `block/large_cable/large_cable`, particle `block/armor` (all D) | original |
| Wrench bundle-selection overlay | `misc/bundle_selection_hatch` (D) | original |
| Mod logo `ae2federation_logo.png` | render of the Router | contains B |

Only the cable family and the selection overlay are free of AE2 art.

Removed from the mod on 2026-10-22 (owner approved), because nothing drew them: `block/glass`, `block/collar`,
`block/stream_u`, `block/stream_v` (the pre-artist V07 glass-tube cable, unused since the dense cable of
2b0b5fbc), `part/cable/dense/connector` (the artist's AE2-style cap on machine fronts, dropped the same day in
9aed66ab at the artist's suggestion), `part/cable/dense/stream_u/v` (byte copies of the block stream strips), and
`entity/cable_flow` (the first flow renderer's texture, unused since 54cf2ba7), which moved to
`tools/blender/resources/` for the old Blender beam studies. Group D is now 15 textures, 48 in all.

2026-10-23: `block/armor` (group D) was deleted too; the cable block model's particle now uses the artist's
`part/cable/dense/core`. Group D is 14 textures, 47 in all.
