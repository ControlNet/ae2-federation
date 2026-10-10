---
navigation:
  parent: examples/index.md
  title: A Regional ECO Warehouse
  icon: neoecoae:storage_system_l4
  position: 80
---

# A Regional ECO Warehouse

**Goal:** keep a whole region's stock in one Neo ECO storage system, and let districts use it through a hub. Each
district has a Bridge of its own to the hub and a rule with the hub only. It is two Bridges from the warehouse, in
another Federation domain, and the hub passes the warehouse on with re-export. This page appears because Neo ECO AE
Extension is installed.

**You need:**

* the warehouse network: a formed ECO storage system, with its controller
  (<ItemLink id="neoecoae:storage_system_l4" />), its drives (<ItemLink id="neoecoae:eco_drive" />) holding ECO storage
  matrices such as <ItemLink id="neoecoae:eco_item_storage_cell_16m" />, and its
  <ItemLink id="neoecoae:storage_interface" /> on the warehouse's cable; power.
* the hub network: an ME cable and nothing else.
* each district network: a terminal on its cable, with no storage and no power of its own.
* a <ItemLink id="ae2federation:bridge" /> between the hub and the warehouse, and one between each district and the
  hub.

<GameScene zoom="2" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/eco_warehouse.snbt" />
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="6 3 3">
    The warehouse: the smallest ECO storage system, its interface at the back on the warehouse's cable, and the energy
    cell that powers all three networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="6 1.25 2.25" max="6.375 1.75 2.75">
    The hub's Bridge to the warehouse: on the hub's cable, its outer side touching the warehouse's cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="6.375 1 2" max="8 2 3">
    The hub: cable only, with no storage, CPU or power of its own
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="8 1.25 2.25" max="8.375 1.75 2.75">
    The district's Bridge: on the district's cable, its outer side touching the hub's cable
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="8.375 1 2" max="10 2 3">
    The district: a terminal, and nothing else
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Each Bridge makes a domain of its own with the two networks it joins. Right-click the district's Bridge: its screen
shows this domain, the district and the hub. Pick "with connected domains (read-only)" on the scope button, and the
hub's domain with the warehouse joins it on a plate of its own, read-only:

<FederationTopology>
  <Network key="district" label="District" color="#915dcd" column="0" row="0" details="Terminal" />
  <Network key="hub" label="Hub" color="#5CA7CD" column="1" row="0" details="Cable only" />
  <Network key="warehouse" label="Warehouse" color="#5ccd78" column="2" row="0" details="ECO storage system|Energy cell" />
  <Rule user="hub" source="warehouse" capability="storage" state="reexport" />
  <Rule user="district" source="hub" capability="storage" />
  <Energy first="warehouse" second="hub" />
  <Energy first="hub" second="district" />
  <Domain key="d1" label="This domain" networks="district,hub" opened="true" />
  <Domain key="d2" label="Domain 3C91" networks="hub,warehouse" />
</FederationTopology>

## Build it

1. **Build the storage system** as Neo ECO's guide shows for the
   [storage system](neoecoae:neoecoae_intro/storage_system.md); the smallest is 5 blocks long, 3 high and 2 deep. Put
   ECO storage matrices into its drives, and connect its interface and an energy cell to the warehouse's cable.
2. **Join the hub to the warehouse** with a Bridge on the hub's cable, its outer side touching the warehouse's cable.
3. **Right-click that Bridge.** Switch on Storage under "Hub uses Warehouse's" and step it on once more, to Enabled
   with re-export. Switch on **ME power** for the pair too.
4. **Join the district to the hub** with a Bridge on the district's cable, its outer side touching the hub's cable.
   Right-click it: its screen shows only the district and the hub. Switch on Storage under "District uses Hub's", and
   ME power.
5. **Open the district's terminal.** The warehouse's items are listed. Take some out, and put something in: with no
   storage of its own, the district stores it in the warehouse.

## Two Bridges away

* The district and the warehouse share no domain, so no screen has a switch for "District uses Warehouse's", and
  none is needed. What the district reaches is decided by two rules: "District uses Hub's" in its own domain, and
  "Hub uses Warehouse's", with re-export, in the hub's domain with the warehouse. Change that one from the hub's
  Bridge to the warehouse; the district's screen shows it read-only.
* More districts join the same way, each with a Bridge of its own to the hub and its own "uses Hub's" Storage rule.
  The warehouse keeps one Bridge and one rule however many districts there are.
* Power pools along the chain: with ME power on for both pairs, all three networks run on the warehouse's energy cell.
  ME power has no re-export; the pools simply join up.

## Try it

Step "Hub uses Warehouse's" Storage back to Enabled. The warehouse's items disappear from the district's terminal,
while the hub still sees them under its own rule. Step it to re-export again and they come back.
