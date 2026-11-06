---
name: audit-and-fix
description: Audit a software project, verify bugs, coordinate parallel fixes in isolated Git worktrees, integrate reviewed commits, and verify delivery through remote CI. Use for project health checks with remediation, batches of bug fixes, or autonomous multi-agent debugging. Adapt investigation and validation to the project; honor review-only, local-only, and repository-specific delivery constraints.
---

# Audit and Fix

Own the full loop from investigation to verified delivery. Let the main agent choose technical methods, scope, workstreams, and tests from the actual project. Do not require a fixed bug count, language, framework, model, test runner, or hosting platform.

## 1. Establish the baseline

- Read repository instructions, relevant design decisions, recent changes, and existing validation workflows. Treat reported defects as leads, not established facts.
- Record the repository, starting commit, current branch, upstream, and working-tree changes. Use the starting branch as the integration target unless instructed otherwise; never assume it is named main or master.
- Preserve unrelated and pre-existing changes. Do not stash, reset, commit, or publish someone else's work implicitly. Use an isolated integration worktree when needed; stop only the affected integration step if ownership cannot be resolved safely.
- Establish the requested delivery scope. For an authorized end-to-end repair, default to worker commits, integration, push, and CI verification. Explicit review-only or local-only requests take precedence. Skill invocation cannot bypass permissions, protected branches, or repository rules. Use a permitted PR workflow when required and authorized.
- Select a bounded audit scope and completion criteria. Make reasonable choices without repeatedly requesting confirmation. If detached or lacking an unambiguous push target, continue useful local work and resolve that specific delivery ambiguity before pushing.

## 2. Investigate and qualify findings

Delegate independent investigation areas when useful and supported. Choose areas from the project's risks and architecture, not a prescribed checklist. Give reviewers the relevant artifacts and expected behavior, without requiring them to confirm a proposed diagnosis.

For each candidate, keep a compact record:

- Finding ID, location, trigger, and user-visible consequence.
- Expected behavior and its basis in requirements, contracts, or a reliable reference.
- Evidence level: reproduced, supported by a concrete code path, or unverified hypothesis.
- Reproduction or validation approach, severity, and relevant dependencies.

Trace the complete path, including existing safeguards. Seek counterexamples to the diagnosis. Distinguish bugs from intended behavior, obsolete requirements, and optional improvements. For performance findings, identify workload and repeated work; measure before claiming a bottleneck or improvement.

Deduplicate shared causes. Turn credible findings into reproductions or targeted checks where practical. Report uncertainty honestly when execution is unavailable. Do not manufacture findings to fill a quota or make speculative redesigns to satisfy a reviewer.

## 3. Assign repair workstreams

Group findings by shared cause, interfaces, and code ownership. Keep tightly coupled state changes together; parallelize independent work. Agree on shared interfaces first or land prerequisite changes before dependent work begins.

For each repair worker, provide:

- Goal, finding IDs, expected behavior, and constraints.
- Base commit, assigned branch/worktree, and owned scope.
- Dependencies, validation expectations, and handoff requirements.

Use a unique branch and worktree per concurrent writer. Workers must not switch, merge, reset, or edit another worker's worktree. Isolate mutable test resources such as databases, ports, worlds, outputs, and runtime directories. Limit heavyweight concurrency; run comparative benchmarks under comparable load.

Workers may commit their own scoped changes, but must not push or integrate into the target branch. If subagents or worktrees are unavailable, execute the same logical stages sequentially and disclose that limitation. Do not force parallelism for a small or tightly coupled repair.

## 4. Repair and validate

Require each worker to establish the cause, implement a focused repair, and verify the intended behavior. Prefer a failing-before/passing-after regression for a genuine behavior bug when practical. Select validation proportional to the change; do not add tests that merely repeat the implementation.

Preserve project contracts and existing abstractions. Avoid unrelated refactors, weakened assertions, skipped checks, or inflated timeouts as substitutes for correctness. Fix faulty fixtures only with an independent explanation of why the fixture was wrong.

Keep evidence tied to the tested source state and environment. Distinguish mocks, integration tests, real dependencies, and production-like execution. Do not imply that one proves the others.

Before handing off, inspect the diff, stage only owned changes, and create reviewable commits. Return:

- Finding IDs and disposition, root cause, and behavior changed.
- Worktree/branch, base SHA, and commit SHAs.
- Validation commands and outcomes, with logs or artifacts when useful.
- Remaining limits, interface changes, and integration dependencies.

If no defect exists, provide the counterevidence rather than an unnecessary patch.

## 5. Review and integrate

The main agent remains accountable for accepting findings and fixes. Inspect code and tests, not only worker summaries. Obtain independent review of significant or high-risk changes from an agent other than their author. Ask that reviewer to test the contract and seek counterexamples, not simply approve the patch.

Integrate accepted commits in dependency order using the repository's merge, fast-forward, or cherry-pick convention. Resolve conflicts semantically; a clean textual merge is not proof of compatible behavior. Revalidate affected interactions after conflict resolution. Do not create an empty extra commit merely because integration occurred; commit actual integration changes when needed.

Track each finding as fixed, disproved, deferred, or blocked, with a reason. Reopen repairs when evidence is insufficient. Address regressions caused by this work; record unrelated new findings for a subsequent bounded pass instead of expanding indefinitely.

## 6. Verify the combined result and deliver

- Validate the integrated source state, not merely the individual worker branches. Choose targeted, integration, build, static, and runtime checks according to project risk and required gates. Run broader suites when they resolve a real remaining risk or are required.
- If code changes during verification, identify which results remain valid and rerun affected checks. Do not describe receipts from different source states as one final-version run.
- Before pushing, inspect the complete outgoing commit range and confirm remote and branch. Include only authorized changes. If the remote advanced, incorporate it without overwriting others and revalidate affected behavior; do not force-push to bypass divergence.
- Push through the authorized delivery path. Do not deploy, publish a release, or merge a protected PR merely because ordinary CI passed; follow the user's delivery scope and the repository workflow.
- Track remote checks for the exact pushed SHA, or explicitly identify the PR merge SHA when the platform tests a synthetic merge. Confirm the checks cover the intended changes. An earlier green run, a queued run, or no triggered workflow is not a successful validation.
- Fix failures caused by this work, commit, push, and follow the replacement run. Diagnose infrastructure failures separately; retry only with a reason. Do not disable gates to obtain green status. If CI is absent, inaccessible, or externally blocked, report local results and the precise remote-verification limit.
- After each delivered audit round, promptly remove task-created worktrees that are no longer needed; do not accumulate completed worker worktrees until the overall audit ends or wait for the user to remind you.
- Before removal, confirm the worker and its processes have stopped using that directory, inspect its current Git status, verify that accepted commits are integrated (including cherry-picked equivalents), and copy needed ignored evidence outside it. Preserve unintegrated fixes and failing reproductions. Retain active or dirty worktrees with a concrete reason; never force removal to bypass these checks.
- Use `git worktree remove <path>` for eligible worktrees, then verify `git worktree list`. Remove only worktrees and temporary resources created for this task; leave the user's unrelated worktrees untouched. Do not use `git clean`. Report any retained audit worktrees and why they are still needed.

Finish when the agreed findings have supported dispositions, accepted fixes are integrated, relevant validation is complete, and delivery is verified or explicitly blocked. Do not claim the project is bug-free.

## Final report

State the outcome first. Include the integrated branch and final SHA, findings fixed or disproved, meaningful validation and its limits, push/PR status, and CI links with their tested SHAs and results. Identify remaining blockers or deferred work clearly. Use the user's language and keep routine execution details out of the summary.
