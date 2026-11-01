# Bilingual promotional overviews

docs/gallery/overview-zh.webp and overview-en.webp are long posters for Bilibili posts, MC百科, forum threads
and the README. The first version (Blender studio renders, 1800 × 4320, tools/blender/promo.py) was rejected by
the user as ugly and unreadable to outsiders and was replaced the same day. Its script was removed; the old
posters and script are kept locally under build/promo-check/replaced/.

## Current delivery

- 1200 CSS pixels wide, captured at 2x: zh 2400 × 10752, en 2400 × 11166, WebP q94 m6, about 1.6 MB each.
  There is no height cap any more; render.mjs fails on overflow of any `[data-check]` element instead
  (chips must stay inside their `[data-bounds]` scene).
- Sections: hero (Market Hub), 01 shared storage (Shared Warehouse + sharing-rule panel), 02 orders across
  networks (Order Desk, Endpoint Furnaces, mapping view), 03 Nether Outpost (both dimensions), 04 topology and
  statistics, then a CurseForge footer.
- Pipeline: tools/visual/promo/scenes.py -> prepare.py -> render.mjs -> export.py, documented in
  docs/gallery/PROMO.md. Scene rendering details are in promo-guide-scene-renders-2026-10-26.md.
- Font: Noto Sans SC variable TTF (OFL) from google/fonts, kept in build/promo-fonts/. The pixel-style font was
  rejected as unreadable.
- Design canvas (private artifact): claude.ai/artifact/UBoqDEv5ikhtuDzuhXxraV.

## User rules for the poster

- One background colour (#0a0f16) for the whole page; no alternating section bands.
- No Beam Converter (placeholder texture). No teaser image or night beam photo: outsiders could not read them.
- Show the real UIs: topology with the sharing-rule panel, the Processing Endpoint mapping, statistics.
- CurseForge link, not Modrinth.
- Chinese copy says 一个网络 / 两个网络, never 一张网. Copy was tightened with the ccf-humanization skill.
- ME energy sharing is one setting per pair of networks, not per direction (storage and crafting are per
  direction).

## Layout lessons

- Use `text-wrap: balance` for headings and `pretty` for paragraphs; CJK orphans still need an explicit `\n`
  in copy.json (the zh hero paragraph has one).
- Anchor chips near the right edge with `right:` so longer English labels stay inside the scene.
