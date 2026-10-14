# Federation and AE network statistics dashboard

Discussion date: 2026-10-10.
Status: preliminary user-proposed idea; metrics and detailed UX remain open.

## Intent

Provide a dedicated statistics interface for understanding Federation and AE networks through visualizations.
The user requested time-series charts and dashboard-like views, rather than only textual status or current
values. Keep this as its own feature idea.

## User-proposed direction

- Show statistics about the Federation and connected AE networks.
- Provide time-series views so players can inspect changes over time.
- Combine graphical views into a dashboard-like experience.

## Candidate visualizations for discussion

The following are assistant suggestions, not a confirmed metric list or claims of existing data collection:

- Inventory history for selected resources and networks.
- Resource transfer rates, with direction and resource units identified.
- Crafting activity, completion counts, and observed task durations.
- Energy history where the relevant measurements can be obtained reliably.
- Per-network comparisons and a Federation overview with drill-down into one network.

Different resources should not be summed into a single unexplained throughput value. Shared or re-exported
storage views can overlap, so Federation totals need an explicit counting scope rather than adding every
network's visible inventory. Current state, measured history, and inferred values should be distinguishable.

## Open questions

- Which metrics can be observed accurately, and does each describe local ownership or accessible resources?
- Which charts and navigation patterns are most useful for the initial interface?
- What sampling intervals, history retention, and save/restart behavior are appropriate?
- How should unloaded networks and missing measurements appear without being mistaken for zero activity?
- What history is retained by default versus explicitly tracked by the player?

This idea does not prescribe a new block/item, registry ID, editor, charting library, storage backend,
customizable dashboard layout, or integration with the automation system. It does not expand the separately
scoped lightweight Jade tooltip. No implementation or historical-data availability is asserted.

## Reference: Singularity Iteration

The user suggested [Singularity Iteration](https://github.com/2132937983/Singularity-Iteration).
Source inspection of revision `b355c64facb419987698333f72fe5da49872a357` confirms an energy terminal
with summary indicators, recent-history charts, local/global scope, consumer rankings, and device details.
This is a concrete reference for the proposed dashboard direction.

Candidate lessons include overview + trends + details, resource icons, explicit measurement scope,
and collecting data separately from rendering. Its 112-sample energy history is held in memory rather
than persisted by the terminal; it does not establish long-term AE2/Federation statistics availability.
Its remote device controls are not automatically included in this idea. No layout, sampling interval,
new terminal block, or implementation framework is selected by citing this reference.

See [source inspection and limitations](../../.omo/knowledges/singularity-iteration-statistics-reference-2026-10-10.md).
