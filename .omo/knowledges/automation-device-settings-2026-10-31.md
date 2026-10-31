# Automation: device settings (B, 2026-10-31)

Programs change AE2 device settings, priority and config/filter slots through generic `ae2.*` calls.
Code: `automation/DeviceSettings.java` (names, values, display table), `automation/DeviceCalls.java` (runtime),
`script/Completion.java` + `script/Checker.java` (dependent argument), `StandardLibrary.DEPENDENT`.

## API

- `ae2.settings(block) -> list[Setting]` (record: name, value, values), `ae2.setting(block, name) -> Optional[SettingValue]`,
  `ae2.set_setting(block, name, value) -> bool` (action).
- `ae2.priority(block) -> Optional[int]`, `ae2.set_priority(block, priority) -> bool` (action, `IPriorityHost`).
- `ae2.config(block) -> list[Optional[Stack]]`, `ae2.set_config(block, slot, resource=None, amount=1) -> bool`
  (action, `IConfigInvHost.getConfig()`); omitting the resource empties the slot; a resource the slot refuses gives False.
- The device: the part on the selected side when the block entity is an `IPartHost`, else the block entity. Only AE2
  API interfaces, so addon devices work. The interface's own block is refused (reads empty, changes False).
- Reads give None/empty for a missing setting; changes raise ValueError listing what the device has or accepts.

## Dependent completion

- `StandardLibrary.DEPENDENT` maps a typed string to the typed string it depends on (`SettingValue -> SettingName`).
- `Completion.argumentType` records argument start indexes; `literal(...)` finds the source argument (positional or
  keyword) before the cursor and takes it only when it is a single STRING token. No literal (a variable, a
  concatenation) -> no value candidates. `Catalog.values(type, of, prefix)` supplies them.
- `Checker.bind()` -> `dependents(...)`: a literal value the literal setting lacks is a problem; an unknown setting
  name is not checked there (the name check reports it).

## AE2 facts found on the way

- `Settings.register` is private; the registry is read by reflecting over `Settings`' public static `Setting`
  fields. @Deprecated fields (e.g. inscriber buffer size) are skipped.
- Terminal display preferences (SORT_BY, SORT_DIRECTION, VIEW_MODE, SEARCH_TOOLTIPS, TERMINAL_STYLE,
  TERMINAL_SHOW_PATTERN_PROVIDERS, POWER_UNITS) are left out, also at run time.
- `io_direction` and `level_emitter_mode` stay in the registry but no AE2 device has them (still offered by name).
- `Setting.getValues()` is in enum ordinal order: FuzzyMode is ignore_all, percent_99, percent_75, percent_50,
  percent_25 (not ascending). REDSTONE_EMITTER allows only low_signal/high_signal.
- Bus config inventories (IOBusPart `updateState`, StorageBusPart `onConfigurationChanged`) do not mark the host for
  save; `DeviceCalls.changed()` calls `blockEntity.setChanged()` after every change. Setting changes on
  UpgradeablePart, InterfaceLogic and PatternProviderLogic save themselves.
- GameTest `helper.assertValueEqual(x, null)` throws an NPE; use `assertTrue(x == null)`.

## UI

- Name candidates: detail = AE2 button title; note = `ae2federation.automation.setting_devices` ("On: ...").
  Value candidates: detail = the button's line for that value.
- `CompletionPopup` wraps notes after ", " list items (never inside a device name), up to `NOTE_ROWS = 4`, and widens
  to about half the note width. Shots: `ui-automation-completion-setting`, `-setting-value`, `ui-automation-library-settings`.

## Not covered

- Saving after a change is not verified by a GameTest (no reload test).
- Completion details list only AE2's own devices (static table); addon settings complete by name only if they are
  in AE2's `Settings`, otherwise they are reachable at run time through `ae2.settings(block)`.
