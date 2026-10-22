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
