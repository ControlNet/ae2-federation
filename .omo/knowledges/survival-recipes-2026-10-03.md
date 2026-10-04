# Survival recipes (2026-10-03)

The user confirmed early access to every device and six recipes. All of them are hand-written JSON, like the rest of
the resources; the repository has no datagen.

| Output | Recipe (`data/ae2federation/recipe/`) |
|---|---|
| 1 Federation Logic Processor | `ae2:inscriber`, `mode: press`: Fluix Dust on top, native Logic Processor in the middle, bottom empty |
| 1 Bridge | shapeless: Storage Bus + Quartz Fiber + Federation Logic Processor |
| 1 Pattern Provider | shapeless: `ae2:pattern_provider` (block item) + Federation Logic Processor |
| 1 Processing Endpoint | shapeless: `ae2:interface` (block item) + Federation Logic Processor |
| 16 Cable | shaped: 8 × `#ae2:glass_cable` (any colour, mixed) around a Federation Logic Processor |
| 4 Router | shaped `CIC / SLN / CEC`: Cables in the corners, Import Bus top, Storage Bus left, Interface right, Export Bus bottom, Federation Logic Processor centre (native Logic Processor until 2026-10-04) |

- The processor is `material/MaterialRegistration.FEDERATION_LOGIC_PROCESSOR`. Its texture is the user-approved icon
  edited from AE2's `logic_processor.png`. It is a derivative of AE2's CC BY-NC-SA 3.0 art,
  distributed like all the mod's art under CC BY-NC-SA 4.0; recorded in `docs/compatibility/dependencies.md`.
- `mode: press` spends the top and bottom inputs. `inscribe` keeps them, like a press plate. AE2's `InscriberRecipe`
  codec makes `top` and `bottom` optional. `InscriberRecipes.findRecipe` also matches the recipe flipped, with the dust
  in the bottom slot.
- The device recipes name the block items, not AE2's `ae2:interface` / `ae2:pattern_provider` tags. The tags would
  also accept the cable-part forms, which the user excluded. Crafting from a native block does not carry over its
  patterns or settings.
- Recipe-book unlocks live in `data/ae2federation/advancement/recipes/misc/`. They unlock when the player holds the
  Federation ingredient: the processor, or the Cable for the Router. The Inscriber recipe has no recipe-book entry. JEI
  and EMI show every recipe either way, and AE2's plugin renders the Inscriber one.

## Automation

A real Inscriber fed through its item handler on one side works. A Logic Processor is not an optional ingredient of
any recipe, so AE2's `BaseFilter` sends it to the middle slot. Fluix Dust is an optional ingredient only of this
recipe, so it goes to the top slot. Reading `BaseFilter` suggests that dust beyond a full top slot is refused rather than
clogging the middle, but no test covers that. AE2's own Fluix crystal → dust recipe takes the crystal in the middle,
so it is unaffected.

## Tests

- `SurvivalRecipeContractTest` (JUnit) pins the JSON as written. The test classpath has no Gson, so it compares
  whitespace-stripped text, like the other contract tests.
- `SurvivalRecipeGameTests.survivalRecipes` (`survival.recipes`, 31 assertions) checks each crafting recipe through
  `RecipeManager.getRecipeFor`, including that our recipe id wins and the cable ring takes mixed colours. A native
  Logic Processor in the ring makes no Federation Cable. It also checks the Inscriber recipe both ways round and the
  five unlocks.
- `survivalInscriber` (`survival.inscriber`, 4) presses two processors in a placed Inscriber.
- Mutation check, 2026-10-03: with `mode` set to `inscribe` both GameTests fail ("Pressing must spend the dust";
  2 inputs left).

```sh
./gradlew :neoforge-1.21.1:test --tests '*SurvivalRecipeContractTest' --dependency-verification=strict --console=plain
python3 tools/dev_gametests.py survivalrecipes survivalinscriber
./gradlew :neoforge-1.21.1:federationVerify -Pcases=survival.recipes,survival.inscriber \
  -PevidenceDir=.omo/evidence/survival --dependency-verification=strict --no-configuration-cache --console=plain
```

## Next

The GuideME pages join AE2's own guide: GuideME lists `ae2guide/` in every namespace, so
`assets/ae2federation/ae2guide/*.md` needs no code. Pages under `_zh_cn/` are picked automatically. Validate with
`-Dguideme.validateAtStartup=ae2:guide`.
