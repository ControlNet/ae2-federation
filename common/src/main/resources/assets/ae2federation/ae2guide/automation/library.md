---
navigation:
  parent: automation/index.md
  title: Library
  icon: ae2federation:automation_interface
  position: 30
---

# Library

Programs reach the game through three namespaces, files through `fs` and the web through `net`. The **Library** panel in the Programs tab lists the same functions,
each with a short example. Functions marked **action** take one game tick each.

A `network` parameter is optional: left out, the call works on every network of the domain, each through its own
storage, whatever the rules share.

## ae2: storage and crafting

| Function | Gives |
| --- | --- |
| `ae2.amount(resource, network)` | how much is stored |
| `ae2.storage(network)` | everything stored, as `Stack`s |
| `ae2.craftable(resource, network)` | whether a pattern makes it |
| `ae2.crafting(resource, network)` | how much running and requested jobs will still put into storage |
| `ae2.patterns(network)` | everything that can be crafted |
| `ae2.free_cpus(network)` | crafting CPUs not busy |
| `ae2.energy(network)` | AE stored |
| `ae2.craft(resource, amount, network)` | **action:** asks for a job; `False` if no pattern makes it or no CPU is free |
| `ae2.cancel(resource, network)` | **action:** cancels the jobs this installation started on the network |
| `ae2.import_from(block, resource, amount, network)` | **action:** moves from the block into storage |
| `ae2.export_to(block, resource, amount, network)` | **action:** moves from storage into the block |
| `ae2.move(source, target, resource, amount)` | **action:** moves between two networks of the domain |

AE2 plans a job over the next ticks, then starts it on a free CPU. A plan that misses ingredients, or finds every CPU
busy by then, starts nothing, and `ae2.crafting` drops back. The job's final output counts in `ae2.crafting` from the
request until it reaches storage.

## ae2f: the Federation

| Function | Gives |
| --- | --- |
| `ae2f.select(interface, reach=1, side=None)` | the block in front of the interface, 1 to 4 blocks away, seen from a side |
| `ae2f.networks()` | the networks of the domain |
| `ae2f.online(network)` | whether the network is usable now |
| `ae2f.rule(provider, consumer, capability)` | the rule's mode: `"off"`, `"on"` or `"reexport"` |
| `ae2f.set_rule(provider, consumer, capability, mode)` | **action:** sets the rule |
| `ae2f.set_filter(provider, consumer, capability, mode, resources)` | **action:** sets what a storage rule passes while it is on |

## mc: the world

| Function | Gives |
| --- | --- |
| `mc.amount(block, resource)` | how much the block holds |
| `mc.storage(block)` | what the block holds |
| `mc.block_id(block)` | the block's ID, such as `"minecraft:chest"` |
| `mc.state(block, property)` | a state property, such as a crop's `"age"`, or `None` |
| `mc.redstone(block)` | the signal the block receives, 0 to 15 |
| `mc.fe(block)` | FE stored in the block |
| `mc.time()` | game time in ticks |
| `mc.move(source, target, resource, amount)` | **action:** moves between two blocks |
| `mc.set_redstone(interface, level)` | **action:** signal from the interface's front, kept until set again |
| `mc.break_block(block)` | **action:** breaks it; the drops go into the domain's storage |
| `mc.place_block(block, item)` | **action:** places the item from the domain's storage |
| `mc.move_fe(source, target, amount)` | **action:** moves FE between two blocks |
| `mc.wait(ticks)` | **action:** pauses the run for `ticks` ticks; it does not use up the tick's action |

## fs: files

Files live in the world's workspace folder, `<world>/ae2federation/workspace`, which every program of the world
shares. A path is relative, with `/` between names, as in `"logs/iron.txt"`; one that leads out of the folder stops
the run. A file holds text of up to 100,000 characters, and the folder at most 16 MB.

The server can turn files off: `allowFileAccess = false` in the `[automation]` section of
`config/ae2federation-server.toml`, where `workspaceSizeMb` sets the folder's size. An `fs` call then stops the run
with `PermissionError`.

| Function | Gives |
| --- | --- |
| `fs.read(path)` | the file's text, or `None` if there is none |
| `fs.exists(path)` | whether the file or folder is there |
| `fs.list(path="")` | the names in a folder, in order; folders end with `/` |
| `fs.write(path, text)` | **action:** writes the file, making its folders |
| `fs.append(path, text)` | **action:** adds to the end of the file, making it if needed |
| `fs.delete(path)` | **action:** deletes a file or an empty folder; `False` if there was nothing |

## net: the web

| Function | Gives |
| --- | --- |
| `net.http(method, url, body="", headers={})` | **action:** sends an HTTP request and gives its `Response` |
| `net.connect(url)` | **action:** opens a websocket connection, or keeps the open one |
| `net.send(url, text)` | **action:** sends a text message on the connection |
| `net.receive(url)` | the oldest message not read yet, or `None` |
| `net.connected(url)` | whether the connection is open |
| `net.disconnect(url)` | **action:** closes the connection; `False` if there was none |

The run waits for the answer without using up actions, at most 10 seconds. Any status, 404 too, is an answer; a
request that gets none, times out or would bring back more than 100,000 characters stops the run. Header names in
the answer are lower case. A dry run sends nothing and gives `Response(0, "", {})`.

A websocket URL starts with `ws://` or `wss://`. Connections belong to the installation: they stay open between runs,
and close when the installation is removed. An installation keeps at most 4 open. `net.connect` and `net.send` wait
like `net.http`; messages arrive in the background, and `net.receive` reads them in order, `None` when none is
waiting. Unread messages beyond the newest 256 are dropped, and one over 100,000 characters closes the connection.
What arrived before a close can still be read. A dry run opens nothing: `net.receive` gives `None`. To start a
run when a message arrives, install it on the event `ws_message`.

In the same `[automation]` section, `allowHttp = false` turns `net.http` off and `allowWebsockets = false` the
websocket functions, closing open connections; their calls then stop the run with `PermissionError`.
`networkTimeoutSeconds` sets how long a request, a connect or a send waits.

Python's built-ins `int`, `float`, `str`, `bool`, `list`, `dict`, `set`, `range`, `len`, `abs`, `min`, `max`, `sum`,
`round`, `any` and `all` work as in Python, as do the methods of lists, strings, dicts and sets. `print(...)` writes one line to the
installation's log.
