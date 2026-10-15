# Pinyin search with JustEnoughCharacters (2026-10-15)

- JECh (NeoForge 1.21.1 branch `1.21.1`, Modrinth project `I7k4B65h`, version 4.5.30 = `iNnJuUnx`, MIT) works
  through a coremod. At build time a script turns its `generate.yaml` list of other mods' methods into
  `targets.json`, and the coremod rewrites `String.contains`/`equals`/regex calls inside exactly those methods to
  `me.towdium.jecharacters.utils.Match`. Anything not on the list gets no pinyin. Lambda names such as
  `lambda$x$12` change between builds, so asking for our methods to be listed is fragile.
- Players can add methods in `config/jecharacters-extra.json` (`additional` lists), with the same lambda-name
  fragility.
- Our hook: `client/policy/SearchText.contains(text, query)` tries the plain lower-case substring first, then
  `compat/jech/JechPinyin`. That class binds `Match.contains(String, CharSequence)` with a public `MethodHandle` once
  `ModList` reports `jecharacters`. Receiver order follows JECh's rewrite of `text.contains(query)`. Pass the
  lower-cased text.
- PinIn is jar-in-jar inside the JECh jar (`META-INF/jarjar/PinIn-1.6.0.jar`), so compiling against JECh would also
  need PinIn. The run-time lookup avoids both, and there is no change to `build.gradle`.
- In-game check: copy the jar into `neoforge-1.21.1/run-uitest/mods/` (gitignored), run `ui.chinese-scales`, then
  delete it. The dev client loads mods from that folder, including JECh's nested coremod. The scenario's check
  expects "wangluo" to match 网络 exactly when JECh is loaded, so it also passes without the jar.
- `ModList.get()` is null in plain unit tests, and `JechPinyin.find()` returns null there.
