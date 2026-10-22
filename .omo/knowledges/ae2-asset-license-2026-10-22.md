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
