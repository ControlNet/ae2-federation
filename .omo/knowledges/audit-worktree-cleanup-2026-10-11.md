# Audit worktree cleanup

The user requested prompt cleanup of finished audit worktrees and asked to persist this practice in the local
`audit-and-fix` skill. Its delivery section now requires cleanup after each delivered round, after checking worker
processes, Git status, integrated commits and saved evidence. Active worktrees, failing reproductions and unintegrated
changes must be preserved with an explicit reason; unrelated user worktrees remain untouched.

Removed the completed `ae2-audit-r2-returns`, `ae2-audit-r2-topology`, `ae2-audit-r2-lifecycle`,
`ae2-audit-coverage` and `ae2-audit-transit-restart` worktrees with `git worktree remove` after checking clean status,
integrated/cherry-picked commits, stopped processes and preserved evidence. The third-round integration worktree
remains only until final delivery/CI and will then be removed. Unrelated Blender and multiblock worktrees are retained.
No force removal or `git clean` was used.
