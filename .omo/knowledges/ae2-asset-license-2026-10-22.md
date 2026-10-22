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
