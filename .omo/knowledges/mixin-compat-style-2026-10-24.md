# Mixin style: compose with other mods' hooks (2026-10-24)

Owner rule: every mixin plans for other mods hooking the same method or call site and picks the most compatible form.

## Rule

- Chainable MixinExtras injectors first: `@WrapOperation`, `@WrapMethod`, `@ModifyReturnValue`,
  `@ModifyExpressionValue`, `@WrapWithCondition`. Avoid `@Redirect`, `@Overwrite`, `@ModifyConstant` and a HEAD
  `cancellable` inject with `setReturnValue`/`cancel`. NeoForge 21.1 ships MixinExtras 0.5.3 (already on the
  classpath: `io.github.llamalad7:mixinextras-neoforge:0.5.3`); add no dependency.
- Read results, don't re-derive them: take the wrapped call's result or the return value, after the other mods'
  hooks ran, not a copy computed at HEAD.
- Observe-only where possible. Accessors and observe-only TAIL/RETURN injects are fine as they are.
- When the original must not run (a replacement), use `@WrapMethod` and skip `original.call` only on the branch that
  replaces it; every other branch calls it, so other wrappers and the method's own injections still run.
- Hooks into an optional mod, or into a target that may change without breaking Federation, use `require = 0`.
- Each mixin's Javadoc names the target version it was verified against (build version, and the compat profiles'
  version when they differ).
- Config: `ae2federation.mixins.json` stays `required: true` with `defaultRequire: 1`. The AE2 and GuideME hooks
  carry Federation behaviour (pushes to an unauthorised neighbour, unsettled return debt, a broken sidebar) that must
  not silently vanish on a changed target; the thread guards opt out per injector with `require = 0`.

## How the 2026-10-24 rewrite composes (branch fix/mixin-compat-style)

| Mixin | Before | After |
|---|---|---|
| `PatternProviderLogicTargetBinding.findAdapter` | HEAD cancellable `setReturnValue` | `@WrapMethod`: bound Lane returns its target, otherwise `original.call(side)` |
| `client.GuideNavBarMixin.recreateRows` | HEAD cancellable, rebuilt rows itself | `@WrapMethod`: `original.call()` first, then nests GuideME's own rows and adds deeper levels |
| `client.GuideNavBarMixin.updateLayout` | `@Redirect` of `LytParagraph.layout` | `@WrapOperation` of the same call |
| `client.GuideNavBarRowMixin.isVisible()Z` | HEAD cancellable `setReturnValue(false)` | `@ModifyReturnValue`: `visible && ancestor shown` |
| `client.GuideNavBarRowMixin.getBounds` | RETURN cancellable `setReturnValue` | `@ModifyReturnValue` |
| `compat.GuideTooltipThreadMixin.handleTooltip` | HEAD cancellable, `require = 0` | `@WrapMethod`, `require = 0`, calls original on the render thread only |
| `compat.LDLibTextThreadMixin.recompute` | HEAD cancellable, `require = 0` | `@WrapMethod`, `require = 0`, same condition |

Trade-off of the findAdapter wrapper: an `@Inject` into the body no longer runs for a bound Lane (the original is
skipped). The testmod's `PatternProviderLogicNativeEvidenceMixin` counted lookups with a HEAD inject and broke 7
GameTests; it is now an outer `@WrapMethod(order = 2000)`. A later `order` nests outside ours.

`compat.CraftingCpuReceiptMixin` (HEAD observe of `CraftingCpuLogic.insert`) was left unchanged: the owner plans one
generic hook on `CraftingService.insertIntoCpus` instead. Notes for that design are in the handoff
(build/mixin-compat-handoff.md, git-ignored) and summarised below.

## Other mods on the same targets (checked with javap)

- Data Energistics 3.3.3/3.3.5 nests GuideME's sidebar too: an inject before `updateLayout()` in `recreateRows`, a
  HEAD cancellable `updateLayout`, HEAD cancellable `Row.isVisible()Z`, RETURN cancellable `Row.getBounds`. Ours steps
  aside when `data_energistics` is loaded (nothing nested, so our wrappers return GuideME's values unchanged).
- `PatternProviderLogic.findAdapter`: OmniSequence 2.0.7 `@Shadow`s it, Data Energistics `@Invoker`s it; both call
  through our wrapper.
- `CraftingCpuLogic.insert` (for the future receipt hook): ae2lt 2.1.x and Useless Mod 2.4.5.10 HEAD capture +
  `@WrapOperation` on the SIMULATE `ListCraftingInventory.extract` (ordinal 0) + cancellable RETURN that claims
  overload/dynamic outputs against other expected keys; Applied Enhancements 1.1.x `@WrapMethod` (retains part of the
  final output in the CPU inventory, extracting the wait itself); OmniSequence `@WrapOperation` on
  `ListCraftingInventory.insert` and `Math.max`; Data Energistics `@WrapOperation` on `CraftingLink.insert`
  (skips requester insertion for virtual completions); AE2 Utility 1.8.0 `@Redirect` of every `ListCraftingInventory
  .extract` in `insert` (fuzzy NBT-tear key) and of `AEKey.matches(GenericStack)`.
- MixinExtras `@WrapOperation` is a late injector and wraps a call another mod already `@Redirect`ed (it targets the
  redirect handler call), which is how ae2lt's wrapper and AE2 Utility's redirect coexist in the NAST pack.

## Verification

- In-game sidebar before/after, with and without Data Energistics 3.3.3: build/mixin-compat-check/ (identical
  layout; only animated item icons differ between frames).
- Client capture recipe: private `Xvfb :113`, `runGuideClient`, wait for `Compiling ae2federation` + 25 s, click the
  Federation triangle at (14, 157), then rows at x=120: How Federation Works y=193, Examples y=247.
