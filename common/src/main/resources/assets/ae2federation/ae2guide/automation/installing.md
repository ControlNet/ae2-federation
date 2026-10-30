---
navigation:
  parent: automation/index.md
  title: Installing and Triggers
  icon: ae2federation:creative_computing_device
  position: 40
---

# Installing and Triggers

A function runs once it is installed on a computing device of the domain. The **Automation** tab of the Federation
screen shows every program as a card on the left and this domain's computing devices on the right.

## Installing

* Press a function's row in a program card and drag: a wire follows the pointer, and the devices light up. Drop it on
  a device to install the function there.
* A function with parameters asks for each argument first: an item by its ID, an interface or network by its ID or
  alias, a number. The same function can be installed many times with different arguments.
* Private functions (`_name`) cannot be installed. Everything an installed function calls comes with it.
* An installation runs the version of its program last pushed. **Push** takes the program together with every program
  it calls, as they are at that moment, and is refused until all of them check clean. A program's square is green when
  the devices run its current text, yellow while an edit waits for **Push**.
* Pushing a called program does not change what other programs took at their own push: push each program whose
  installations should use the edit.

## The installation's settings

Click an installation in its device card. The panel beside it shows its arguments, its settings, whether it is
running, and its log. Click an argument or a number to change it.

**Trigger:** what starts a run.

| Trigger | Starts a run |
| --- | --- |
| Manual | only with **Run now** |
| Redstone | when a redstone signal reaches the computing device |
| Every | every so many ticks |
| Condition | when a `bool` function of the same program, which must not act, turns true |
| Event | when something happens in the domain |

**Events** are typed into their field, which lists the matching ones:

* For a resource: `stock_changed`, `crafting_changed`, `craftable_changed`.
* For the domain's networks: `network_boot`, `power_changed`, `controller_changed`, `cpus_changed`, `spatial_io`.
* For the block an interface points at, up to 4 blocks away: `block_changed`, `crop_grew`, `right_clicked`.
* For a websocket URL: `ws_message`. The installation keeps the connection open, trying again every min gap while
  it is down, and a run starts when a message arrives. The run reads it with `net.receive`.

**Min gap:** a run starts at most once in this many ticks; 20 unless changed. Triggers that come during the gap make
one run after it.

**If running:** what a trigger does while a run is still going.

* **Ignore:** the trigger is dropped. This is the default.
* **Restart:** the run stops and a new one starts. What it already did stays done.
* **Parallel:** another run starts beside it, up to 4 at once.

**Log:** the last 20 entries, newest first: started, done with its result, failed with the error and its line.
`print(...)` adds a line. The log is kept in memory and starts empty after a restart.

## When runs stop

A run stops when its device is unloaded, when the server stops, when the device moves to another domain, when a new
version of its program is pushed, and when the installation's arguments or settings change. The installation stays, and its trigger starts new runs later. Breaking the device removes its installations.

Crafting jobs an installation started keep going after its run stops. `ae2.cancel` reaches them while the installation
exists, until the server restarts.

## Commands

Operators can do the same with `/ae2federation programs`: `list`, `push`, `devices`, `interfaces`, `install`,
`installations`, `uninstall`, `trigger`, `interval`, `overlap`, `run`, `log` and `reload`.
