# Playthrough: NAST fails to load Modular Machinery CR now and then (2026-11-01)

Seen once in `playthrough.yml` (job "regression (projection)", run on c4de1d58): the NAST server stopped during mod
loading after 22 s, before any of our code ran. The "regression (endpoint)" and "topology" jobs of the same run,
with the same mods, loaded fine, and the commit only added a library read.

- Crash: `Mod loading issue for: mmcr` (Modular Machinery CR 0.1.3), `ExceptionInInitializerError` caused by a
  `ConcurrentModificationException` in `LinkedHashMap.forEach` at `cn.howxu.mmcr.registry.ModItems.<clinit>`
  (ModItems.java:49). The same log shows the pack's KubeJS startup script (`mmcr.startup`) registering machines
  through `MachineBuilderJS.register` -> `StartupContentRegistration.registerDynamicControllers` at that time, so
  the map ModItems walks is being added to while it is walked. The bug is in mmcr, not in the harness or our mod.
- Where to look: the run's `playthrough-machines-projection` artifact, `crash-reports/crash-*-fml.txt` and
  `logs/latest.log` (search for `ModItems.<clinit>`).
- Upstream fix to propose (owner submits): walk a copy of the map in `ModItems.<clinit>`, or register the
  KubeJS machines before the item class is first loaded.
