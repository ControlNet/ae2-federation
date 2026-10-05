---
navigation:
  parent: examples/index.md
  title: A Quantum Lab
  icon: advanced_ae:quantum_core
  position: 76
---

# A Quantum Lab

**Goal:** a lab network whose only CPU is an Advanced AE Quantum Computer orders from two workshop networks at once: a
sawmill that makes planks from logs, and a joinery that makes sticks from planks. One order needs both workshops in
turn, and a second order runs on the same Quantum Computer at the same time. This page appears because Advanced AE is
installed.

**You need:** a lab network with a formed <ItemLink id="advanced_ae:quantum_core" />-based Quantum Computer, a
crafting terminal, storage and an energy cell; two workshop networks, each with a pattern provider and a molecular
assembler; a <ItemLink id="ae2federation:router" /> all three touch. How to build the Quantum Computer is in
[Advanced AE's guide](advanced_ae:aae_intro/quantum_computer.md).

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/quantum_lab.snbt" />
  <BoxAnnotation color="#915dcd" min="1 0 1" max="8 7 9">
    Lab: the Quantum Computer, a crafting terminal and the energy cell that powers all three networks
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="5 0 0" max="8 1 1">
    Sawmill: a pattern provider with a pattern from a log to four planks, beside a molecular assembler
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="1 0 0" max="4 1 1">
    Joinery: a pattern provider with a pattern from two planks to four sticks, beside a molecular assembler
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    One Router: each face joins the network it touches
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the three networks and the rules between them:

<FederationTopology>
  <Network key="sawmill" label="Sawmill" color="#5CA7CD" column="0" row="0" details="Planks pattern" />
  <Network key="lab" label="Lab" color="#915dcd" column="1" row="0" details="Quantum Computer|Energy cell" />
  <Network key="joinery" label="Joinery" color="#5ccd78" column="2" row="0" details="Sticks pattern" />
  <Rule user="lab" source="sawmill" capability="crafting" />
  <Rule user="lab" source="sawmill" capability="storage" />
  <Rule user="lab" source="joinery" capability="crafting" />
  <Rule user="lab" source="joinery" capability="storage" />
  <Energy first="sawmill" second="lab" />
  <Energy first="lab" second="joinery" />
</FederationTopology>

## Build it

1. **Join the three networks** on the faces of one Router, as in the scene. The Quantum Computer joins the lab's
   network through any of its outer blocks.
2. **Give each workshop its pattern**: the sawmill a crafting pattern from one log to four planks, the joinery one from
   two planks to four sticks, each in a pattern provider beside a molecular assembler.
3. **Switch on Crafting under "Lab uses Sawmill's" and under "Lab uses Joinery's"**. Each switches its own Storage on.
4. **Switch on ME power** between the lab and each workshop, so both workshops run on the lab's energy cell.
5. **Order sticks, then planks** in the lab's crafting terminal, with two logs in the lab's storage.

## How the jobs run

For the sticks, the Quantum Computer first has the sawmill make planks from a log. They come back to the lab and go
on to the joinery, which makes the sticks. The planks order runs at the same time on the same Quantum Computer, which
takes any number of jobs while it has the crafting storage for them. An AE2 crafting CPU runs one job at a time, so with
one of those the second order would have to wait.

## Try it

Switch "Lab uses Joinery's" Crafting off. Sticks disappear from the lab's craftables, and planks stay, since they
come from the sawmill alone.
