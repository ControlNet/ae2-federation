---
navigation:
  parent: automation/index.md
  title: Library
  icon: ae2federation:automation_interface
  position: 30
---

# Library

Programs reach the game through three namespaces, files through `fs` and the web through `net`. The **Library** panel in the Programs tab lists the same functions,
each with a short example. Functions marked **action** take one game tick each. A **long action** starts as that
tick's action, then the run waits until it finishes; the wait takes no actions. Your own functions that call a long
action are marked as long actions in completion and on the graph.

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
| `ae2.cpus(network)` | the crafting CPUs, as `Cpu`s, with the jobs they run |
| `ae2.free_cpus(network)` | free crafting CPUs that take automation requests |
| `ae2.energy(network)` | AE stored |
| `ae2.craft(resource, amount, network, cpu=None)` | **action:** asks for a job, on a CPU of that name if `cpu` is given; `False` if no pattern makes it or no CPU is free for it |
| `ae2.cancel(resource, network)` | **action:** cancels the jobs this installation started on the network |
| `ae2.import_from(block, resource, amount, network)` | **action:** moves from the block into storage |
| `ae2.export_to(block, resource, amount, network)` | **action:** moves from storage into the block |
| `ae2.move(source, target, resource, amount)` | **action:** moves between two networks of the domain |

AE2 plans a job over the next ticks, then picks a free CPU for it as for any automation request: CPUs set to player
requests only, and CPUs too small for the job, are left out. A plan that misses ingredients, or finds no such CPU by
then, starts nothing, and `ae2.crafting` drops back. The job's final output counts in `ae2.crafting` from the
request until it reaches storage.

With `cpu`, the job runs on a free CPU of that name, the smallest one it fits, never on one set to player requests
only. A crafting unit renamed in an anvil names its CPU. A name no CPU has stops the run with a `ValueError`.

## ae2: device settings

A `block` here is a device in front of an interface: on a cable, the part on the selected side (by default the one
facing the interface); otherwise the block itself, such as an ME Interface or an ME Pattern Provider. Addon devices
work the same way.

| Function | Gives |
| --- | --- |
| `ae2.settings(block)` | the device's settings, as `Setting`s with their `name`, `value` and the `values` they take; empty for a block with none |
| `ae2.setting(block, name)` | a setting's value, or `None` if the device has no such setting |
| `ae2.set_setting(block, name, value)` | **action:** changes a setting, as the device's button does |
| `ae2.priority(block)` | the device's priority, or `None` if it has none |
| `ae2.set_priority(block, priority)` | **action:** sets the priority |
| `ae2.config(block)` | the config or filter slots in order, as `Stack`s; `None` for an empty slot |
| `ae2.set_config(block, slot, resource=None, amount=1)` | **action:** puts the resource in the slot, counted from 0; without a resource, empties it |

Settings are named as AE2 names them, and their values are written in lower case. Completion offers the names, each
with the title of its button in the device's screen and, below the list, the devices that have it; for the value it
offers only the values of the setting named before it. The device itself decides what it has, so an addon's settings
work too, and `ae2.settings` lists them. A setting or value the device does not have, a device without a priority or
config slots, or a slot past its last stops the run with a `ValueError` that says what the device has. A block that
cannot be reached now gives an empty list or `None`, and a change to it gives `False`, as does a resource a slot does
not take, such as a fluid in an item filter. The terminals' display settings, such as sorting, are left out.

`ae2.config` lists every slot a device has, also those its screen shows only with capacity cards; a slot the device
does not use yet still keeps what is put in it. A filter slot keeps no amount; the ME Interface's slots keep the amount
to stock.

| Setting | AE2 button | Values | Devices |
| --- | --- | --- | --- |
| `access` | Input/Output Mode | `read`, `write`, `read_write` | ME Storage Bus |
| `auto_export` | Auto-Export | `yes`, `no` | Inscriber |
| `blocking_mode` | Blocking Mode | `yes`, `no` | ME Pattern Provider |
| `condenser_output` | ME Condenser - Output | `trash`, `matter_balls`, `singularity` | Matter Condenser |
| `copy_mode` | Copy Mode | `clear_on_remove`, `keep_on_remove` | Cell Workbench |
| `craft_only` | Crafting Behavior | `yes`, `no` | ME Export Bus |
| `craft_via_redstone` | Crafting Emitter Mode | `yes`, `no` | ME Level Emitter |
| `crafting_scheduling_mode` | CPU Auto-Selection Mode | `any`, `player_only`, `machine_only` | Crafting Unit |
| `filter_on_extract` | Filtered Operations | `yes`, `no` | ME Storage Bus |
| `fullness_mode` | Operation Mode | `empty`, `half`, `full` | ME IO Port |
| `fuzzy_mode` | Fuzzy Comparison | `ignore_all`, `percent_99`, `percent_75`, `percent_50`, `percent_25` | ME Import Bus, ME Export Bus, ME Storage Bus, ME Formation Plane, ME Level Emitter, ME Interface |
| `inscriber_input_capacity` | Input Slot Stack Size | `one`, `four`, `sixty_four` | Inscriber |
| `inscriber_separate_sides` | Automation Access Mode | `yes`, `no` | Inscriber |
| `io_direction` | Transfer Direction | `left`, `right` | none in AE2 |
| `level_emitter_mode` | | `stored_amount`, `storable_amount` | none in AE2 |
| `lock_crafting_mode` | Lock Crafting | `none`, `lock_until_pulse`, `lock_while_high`, `lock_while_low`, `lock_until_result` | ME Pattern Provider |
| `operation_mode` | Transfer Direction | `fill`, `empty` | ME IO Port |
| `overlay_mode` | Overlay Mode | `yes`, `no` | Spatial Anchor |
| `pattern_access_terminal` | Pattern Access Terminal | `yes`, `no` | ME Pattern Provider |
| `place_block` | Block Placement | `yes`, `no` | ME Formation Plane |
| `redstone_controlled` | Redstone Mode | `ignore`, `low_signal`, `high_signal`, `signal_pulse` | ME Import Bus, ME Export Bus, ME IO Port |
| `redstone_emitter` | Redstone Mode | `low_signal`, `high_signal` | ME Level Emitter, ME Energy Level Emitter |
| `scheduling_mode` | Scheduling Mode | `default`, `roundrobin`, `random` | ME Export Bus |
| `storage_filter` | Report Inaccessible Items | `none`, `extractable_only` | ME Storage Bus |

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
| `mc.break_block(block)` | **action:** breaks it; the drops go into the domain's storage, what does not fit drops there |
| `mc.place_block(block, item)` | **action:** places the item from the domain's storage |
| `mc.move_fe(source, target, amount)` | **action:** moves FE between two blocks |
| `mc.wait(ticks)` | **long action:** pauses the run for `ticks` ticks |

## fs: files

Files live in the world's workspace folder, `<world>/ae2federation/workspace`, which every program of the world
shares. A path is relative, with `/` between names, as in `"logs/iron.txt"`; one that leads out of the folder stops
the run. A file holds text of up to 100,000 characters, and the folder at most 16 MB and 10,000 files and folders. Only plain files are read or
written; anything else at the path, such as a pipe, stops the run with `OSError`.

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
| `net.http(method, url, body="", headers={})` | **long action:** sends an HTTP request and gives its `Response` |
| `net.connect(url)` | **long action:** opens a websocket connection, or keeps the open one |
| `net.send(url, text)` | **long action:** sends a text message on the connection |
| `net.receive(url)` | the oldest message not read yet, or `None` |
| `net.connected(url)` | whether the connection is open |
| `net.disconnect(url)` | **action:** closes the connection; `False` if there was none |

The run waits for the answer without using up actions, at most 10 seconds. Any status, 404 too, is an answer; a
request that gets none, times out or would bring back more than 100,000 characters, in its text or in its headers,
stops the run. A request sends at most 100 headers, of 100,000 characters in all. Header names in the answer are lower
case. A dry run sends nothing and gives `Response(0, "", {})`.

A websocket URL starts with `ws://` or `wss://`. Connections belong to the installation: they stay open between runs,
and close when the installation is removed. An installation keeps at most 4 open. `net.connect` and `net.send` wait
like `net.http`; messages arrive in the background, and `net.receive` reads them in order, `None` when none is
waiting. Unread messages beyond the newest 256, or beyond 1,000,000 characters in all, are dropped, and one over
100,000 characters closes the connection. What arrived before a close can still be read, also once connected again;
an installation keeps the unread messages of at most 4 closed connections, and the one closed longest ago goes first.
A send still going when its wait ends, or when its run stops, closes the connection. A dry run opens nothing:
`net.receive` gives `None`. To start a run when a message arrives, install it on the event `ws_message`.

In the same `[automation]` section, `allowHttp = false` turns `net.http` off and `allowWebsockets = false` the
websocket functions, closing open connections; their calls then stop the run with `PermissionError`.
`networkTimeoutSeconds` sets how long a request, a connect or a send waits.

Python's built-ins `int`, `float`, `str`, `bool`, `list`, `dict`, `set`, `range`, `len`, `abs`, `min`, `max`, `sum`,
`round`, `any` and `all` work as in Python, as do the methods of lists, strings, dicts and sets. `print(...)` writes one line to the
installation's log.
