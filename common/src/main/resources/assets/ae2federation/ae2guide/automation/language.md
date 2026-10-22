---
navigation:
  parent: automation/index.md
  title: The Language
  icon: ae2federation:automation_interface
  position: 20
---

# The Language

Programs are written in plain procedural Python. What a program may use behaves as it does in Python 3; what it may
not use is reported at its line as not supported.

## What is in

* `def` with a type for every parameter, default values, docstrings and comments. The result's type may be left out;
  the checker takes it from the `return`s.
* `if`, `elif`, `else`, `while`, `for x in xs`, `break`, `continue`, `return`, `pass`.
* Assignments, including `+=` and the other arithmetic ones.
* `int`, `float`, `str`, `bool`, `None`, lists, `range`, fields such as `stack.amount`, indexing and slices, and
  methods on lists and strings.
* Keyword arguments, as in `ae2f.select(port, side="down")`.
* A class holds typed fields and no methods, and works as a record: `Order("minecraft:iron_ingot", 64)`.

## What is out

Imports, exceptions (`try`, `raise`), classes with methods, lambdas, comprehensions, generators, dictionaries, sets,
tuples, nested functions and `global`. Functions are defined and called; they cannot be stored or passed as values.

## Types

Every value has a static type, checked before a program runs. Besides Python's own:

| Type | Holds |
| --- | --- |
| `Item`, `Fluid`, `Resource` | an ID such as `"minecraft:iron_ingot"`; `Resource` is any kind, including addon resources |
| `Interface` | an Automation Interface, by ID such as `"interface-k3m9qa"` or by alias |
| `Network` | a network of the domain, by ID such as `"network-082a5c"` or by name |
| `Block` | a block selected through an interface by `ae2f.select` |
| `Stack` | a resource and an amount, as `mc.storage` lists them |
| `Side`, `Capability`, `RuleMode`, `FilterMode` | fixed words such as `"down"`, `"storage"`, `"on"`, `"allow"` |

Amounts are in each resource's own unit: items one by one, fluids in mB.

## Several functions and files

A file holds any number of functions. A function calls another of its file by name, and one of another file as
`file.function(...)`, with no import. A name starting with `_` is private to its file and cannot be installed.

<FederationProgram src="../assets/programs/iron_tools.ae2f" function="refill_iron" />

## How a program runs

* Reads, such as `ae2.amount`, take no time. An **action**, such as `ae2.craft` or `mc.set_redstone`, takes one game
  tick, so a run makes at most one action per tick. Move or craft large amounts in one call.
* `mc.wait(ticks)` pauses the run for `ticks` ticks; `mc.wait(0)` takes no time.
* A run takes at most 10,000 steps a tick and goes on in the next one, so `while True:` cannot freeze the server.
* Integers are 64-bit, a list or string holds at most 100,000 items, and calls nest at most 1,000 deep. A program file
  holds at most 64,000 characters.
