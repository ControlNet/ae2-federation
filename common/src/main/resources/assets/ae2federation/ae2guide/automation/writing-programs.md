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
* A card that acts has a purple head, and a long action's head is amber.
* Click a number, text or variable on a card to edit it.
* Above the namespaces the **Library** lists statements (`value =`, `if`, `for`, `while`, `return`, `break`,
  `continue`), operators, built-ins such as `range` and `len`, values (a list, a dict, a set) and your own functions.
  Drag one onto the canvas, and its card follows the pointer; then drag the card's pins onto a card to put it there.
  Right-clicking the canvas opens the same list with a search, and the picked card lands where you clicked. The × on
  a card removes it.
* Drag a card's value pin onto another port to move the value there. To use a value in two places, store it with
  `value =`, then drag the variable from the strip onto each port; the ports it fits are outlined. Click a
  variable's name on its card to rename it throughout the function.
* An `if` card always has an else output; **+ elif** and **- elif** at its foot add and remove branches.
* While a function is shown, the screen keeps running it as a **dry run**: it reads the real domain but acts on
  nothing. Each card shows the value it gave, cards the run did not reach are dimmed, and a card where it failed is
  edged in red with the error.

## Files

Each program is saved as a text file in the world, `ae2federation/programs/<name>.ae2f`. Files edited outside the
game are read again with `/ae2federation programs reload`.
