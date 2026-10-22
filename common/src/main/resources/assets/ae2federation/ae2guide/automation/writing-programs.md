---
navigation:
  parent: automation/index.md
  title: Writing Programs
  icon: ae2federation:automation_interface
  position: 10
---

# Writing Programs

Open the Federation screen and pick the **Programs** tab on the left. Every program on the server is listed there,
with its problem count in red when it has any. Programs belong to the server, not to one domain or player: every
player sees and edits the same ones.

* **+ New** starts a program. Its name must be a Python name, such as `iron_tools`.
* A program is a file of functions. Click a function under its program to jump to it.
* **Push** sends the program, with the programs it calls, to the computing devices. Installed functions keep running
  the version last pushed, so an edit reaches the devices only when you push the program whose functions are installed.
* **Delete** asks first, and is refused while a function of the program is installed or another program calls it.

## The script

The script is Python. The editor colours it, lists each problem under it with its line, and completes names as you
type; **Ctrl+Space** asks for completion anywhere. Completion knows the library, the other programs' functions, item
and fluid IDs, and the interfaces and networks of this domain.

## The graph

**Script** and **Graph** at the top right switch the view of the same program.

<FederationProgram src="../assets/programs/keep_stocked.ae2f" />

* Each function has a tab, and its variables are listed in a strip.
* A card is a call, an operation or a statement. Light wires between the triangular pins show what runs next; the
  other wires carry values, coloured by their type.
* Click a number, text or variable on a card to edit it.
* Drag a function from the **Library** onto the canvas, then drag its pins onto a card to put the call there. The
  × on a card removes it.
* While a function is shown, the screen keeps running it as a **dry run**: it reads the real domain but acts on
  nothing. Each card shows the value it gave, cards the run did not reach are dimmed, and a card where it failed is
  edged in red with the error.

## Files

Each program is saved as a text file in the world, `ae2federation/programs/<name>.ae2f`. Files edited outside the
game are read again with `/ae2federation programs reload`.
