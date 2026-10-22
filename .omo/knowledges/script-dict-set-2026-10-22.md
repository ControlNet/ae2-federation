# Dicts and sets in the program language (2026-10-22)

Added before the outside-IO work (`fs`, `net.http`, websockets) so headers and JSON can use dicts later.

## Shape

- Values: `DictValue` (insertion order, entries keyed by `Ops.hashKey`, first-written key kept) and `SetValue`
  (insertion order, stricter than CPython's arbitrary order). Both carry a `version` that `Runner`'s `for` cursor
  checks, giving CPython's three messages: "dictionary changed size during iteration", "dictionary keys changed
  during iteration", "Set changed size during iteration". A loop walks a snapshot of the keys.
- `Ops.hashKey`: `True`/`1`/`1.0` are one key; lists, dicts, sets and records raise `unhashable type`.
- Cap: `Ops.growMapping`, "dicts and sets hold at most 100000 items" (MemoryError), as lists and strings.
- Types: `dict[K, V]`, `set[T]`; keys and items must be numbers or strings (typed strings and Optional too). Empty
  `{}` and `set()` need an annotation. Dict and set are covariant in `assignable`, as lists.
- Methods: `Checker.DICT_METHODS` / `SET_METHODS`, positional only. `keys()`/`values()` return lists. `items()` is
  refused (no tuples). No set operators (`|`, `&`, `-`, `^`). `Checker.methodsOf(type)` is the one lookup used by
  Completion and GraphCards.
- `d[1:2]` says "a dict cannot be sliced" (CPython 3.12 would look the slice up and raise KeyError).
- Parser: `{1: 2, 3}` and `{1: }` give CPython's own messages; comprehensions and `**` unpacking are refused.

## Every expression walker needs the two literals

Only `Evaluator`'s switch fails to compile on a new `Expr`. These use `default` branches and were extended by hand:
`Compiler.suspends`/`lift` (dict parts interleaved key, value to keep Python's order), `Flow`, `Effects` (Cost uses
it), `DryRunNotes`, `Completion.infer`, `Canvas` (kinds DICT with ports `key<i>`/`value<i>`, SET with `item<i>`),
`Layout`, `GraphCards`, `Edits` (SET items can be added/removed like a list's; DICT entries only replaced).

## Tests and check

- CPython 3.12.2 produced the expected values in `EvaluatorTest.cpythonDictsAndSets` and the RunnerTest programs.
- In-game: `build/dict-set-check/before-after.png` (throwaway `ui.dict-set-check` scenario, not committed; the
  before run used a detached worktree at the previous feature commit).
- Known, not changed: a dry run shows each card's *last* value, so `crafted: dict[...] = {}` shows the filled dict
  at the end of the run. Lists behave the same.
