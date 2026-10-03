# Survival documentation migration

The user confirmed survival playability was implemented and asked about moving the idea. Source inspection found
six recipe resources, English and Simplified Chinese GuideME pages with structure assets, and the runtime processor
texture. The old idea still described these as unimplemented and the cable recipe as unresolved.

- Current behavior: `docs/features/survival-playability.md`.
- Historical discussion: `docs/archive/ideas/survival-playability.md`, explicitly marked historical.
- Selected artwork: `docs/art/assets/federation_logic_processor.png`, byte-identical to the runtime texture.
- The idea index now lists this under implemented ideas rather than future work. README links the current feature
  and clarifies that JEI users require AE2 JEI Integration for machine recipe display.

Actual cable recipe: eight inputs matching `ae2:glass_cable` around one Federation Logic Processor produce sixteen
Federation Cables. The current setup budget accounts for whole cable and Router batches, unlike earlier proposals.
Existing historical knowledge links now point to the archived discussion; their chronological decisions are preserved.

Validation: local Markdown links in the six migrated/updated documents resolve, selected and runtime PNG bytes match,
and `git diff --check` passes. No runtime code changed and no gameplay tests were rerun for this documentation task.
