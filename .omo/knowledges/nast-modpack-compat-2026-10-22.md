# New Age Science and Technology (NAST) compat profile, 2026-10-22

- Pack: CurseForge project 1582045, `new-age-science-and-technology`. The client zips are manifest-only (~18 MB). The
  author attaches a server pack as an additional file: 2.2.1 client file 9286722 → server file 9286724
  `nast server 2.2.1.zip` (383 MB).
- Find server packs without an API key: `https://www.curseforge.com/api/v1/mods/<project>/files/<file>/additional-files`
  (needs a browser User-Agent). The CDN URL is `https://mediafilez.forgecdn.net/files/<first 4 digits>/<rest>/<name>`.
  Pin it with `python3 tools/compat_run.py pin url <url>`.
- The server zip holds `mods/` (226 jars plus packwiz `.index`), `config/`, `kubejs/`, `ldlib2/` and a NeoForge
  installer jar. Its `install_profile.json` gives the version: NeoForge 21.1.251.
- It ships LDLib2 2.2.43, so the profile adds no mods. AE2 19.2.18, ae2lt 2.1.2-beta4 + Thunderbolt 2.0.3-beta3,
  ExtendedAE 2.2.41, ExtendedAE-Plus 1.6.3, AdvancedAE 1.6.12, MEGA 4.11.0, Neo ECO 21.2.9, Data Energistics 3.3.5,
  AppliedFlux 2.1.6, AppMek 1.6.3, Mekanism 10.7.19, Ender IO 8.2.12-beta, IF 3.6.39. No Create.
- Result: 115/115 in about 5 minutes with 8G of heap (abd30f05).

## ae2lt 2.1.2 Tianshu Port

- ae2lt 2.1.2-beta4 moved the Tianshu Supercomputer Port in `assemblies/tianshu_supercomputer.snbt` from (3,0,3) to
  (3,6,3). Its guide says either candidate is allowed. `placeAssembly` now swaps the Port into (3,0,3).
- The failure surfaced as "MatrixPort refused a pattern". `RouterCraftingScene` stage 4 installed the provider's
  patterns before it checked the consumer's CPU. Every retry installed again until the 36 slots were full. CPUs are
  now checked first.
- ae2lt 2.1.2-beta4 requires LDLib2 2.2.42 or newer. On Modrinth, look up LDLib2 by project ID `B1CBVXHX`; the slug
  `ldlib2` gives a 404.
