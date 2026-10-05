# Mixin inventory (2026-10-05)

Read-only source/configuration audit of the working tree, based on HEAD `6bdca3d` plus concurrent uncommitted changes.
No build or runtime injection audit was performed.

- Production configuration: 11 classes/interfaces, comprising 7 common and 4 client entries.
- Testmod configuration: 32 classes/interfaces, comprising 31 common and 1 client entry.
- Total: 43 configured mixins, matching the 43 Java files declaring @Mixin in project source trees.
- Current production GuideBuilderMixin and its configuration entry are uncommitted concurrent work; HEAD has 10
  production entries (7 common, 3 client). Counts refer to this inspection snapshot, not a previously published jar.
- Production annotation declarations: 9 Inject, 2 WrapMethod, 1 WrapOperation, 5 Accessor. This is 12 behavior-hook
  declarations plus 5 field-access declarations, not a count of runtime matched bytecode instructions. The delegating
  inventory mixin exposes a Shadow method through an added interface and is not included in those 17 declarations.
- No production Redirect or Overwrite annotations were found. Testmod annotations: 50 Inject, 3 Redirect,
  2 ModifyReturnValue, 1 ModifyVariable, 1 Accessor, 1 Invoker.
- Production targets: 7 mixins on AE2 (5 distinct target classes), 2 on GuideME, 1 on LDLib2, 1 on Minecraft.

## Production purposes

| Mixin | Target | Purpose |
|---|---|---|
| CableBusIdentityInitializationMixin | AE2 CableBusContainer | Wrap addPart/addToWorld in network identity initialization scope |
| PatternProviderLogicAccess | AE2 PatternProviderLogic | Access patterns, patternInputs and sendList |
| PatternProviderLogicTargetBinding | AE2 PatternProviderLogic | Bind authorized remote target; suppress adjacent-machine delivery for bound lanes |
| StorageServiceNotificationMixin | AE2 StorageService | Forward inventory notifications and reconcile subscriptions at tick end |
| DelegatingMEInventoryAccessMixin | AE2 DelegatingMEInventory | Read the delegate to establish storage alias identity |
| StorageServiceMountLedgerMixin | AE2 StorageService | Expose mount tables/generation; reconcile native mount changes |
| StorageServiceProviderStateMixin | AE2 StorageService$ProviderState | Record real mount/unmount events |
| LDLibTextThreadMixin | LDLib2 TextElement | Prevent off-client-thread text measurement |
| GuideTooltipThreadMixin | GuideME OpenGuideHotkey | Prevent interactive tooltip/font work on other threads |
| MouseHandlerAccess | Minecraft MouseHandler | Restore cursor coordinates across screen switches |
| GuideBuilderMixin | GuideME GuideBuilder | Add FederationTopology tag compiler specifically to ae2:guide |

Production config is required=true and defaultRequire=1. LDLibTextThreadMixin and GuideTooltipThreadMixin explicitly
use require=0 for their injections; this relaxes injection match counts, not the entire dependency/class contract.
Accessor and Shadow targets still need compatibility checks. GuideBuilderMixin is not marked with require=0.

The production jar uses main output. Release archive verification rejects test paths/configuration; compatTestJar
also excludes the testmod mixin configuration and test mixin package. This audit inspected build configuration,
not the contents of a newly built jar.

Risk judgment: prioritize AE2 private mount state and PatternProviderLogic control flow during ports or compatibility
reviews. Wrapper composition is preferable to exclusive redirects but is not proof of compatibility. Counts alone do
not measure risk; test instrumentation should not be counted as modifications shipped to players.

Sources: common/src/main/resources/ae2federation.mixins.json,
common/src/testmod/resources/ae2federation_test.mixins.json, their listed Java sources, and
neoforge-1.21.1/build.gradle.
