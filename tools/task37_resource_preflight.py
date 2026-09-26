# pyright: basic
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


LAYOUTS = {
    "native-big-grid": "DIRECT",
    "native-subnet": "SUBNET",
    "federation": "FEDERATION",
}
PREFIX = "AE2F_SCALE_LARGE_{}_256"
FIELDS = re.compile(r"([A-Za-z][A-Za-z0-9]*)=([^\s]+)")
EQUAL_FIELDS = (
    "cpuBytes",
    "driveCellBytes",
    "totalItemStorageBytes",
    "machineCount",
    "machineInputSlotsPerHost",
    "machineUnitsPerTick",
    "energySupplyAePerTick",
    "worldLoadedChunks",
    "worldLoadedChunksPeak",
    "worldChunkTickets",
    "worldChunkTicketsPeak",
)
OBSERVED_FIELDS = (
    "energyDebitAe",
    "peakEnergyDemandAePerTick",
)
CAPACITY_FIELDS = EQUAL_FIELDS + OBSERVED_FIELDS


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def number(fields: dict[str, str], name: str, expected: int) -> None:
    value = fields.get(name)
    if value is None or not value.isdecimal() or int(value) != expected:
        raise ValueError(f"{name}: expected observed {expected}, got {value!r}")


def one_row(lines: list[str], marker: str) -> str:
    rows = [line.split(marker, 1)[1] for line in lines if marker in line]
    if len(rows) != 1:
        raise ValueError(f"{marker}: expected one receipt, got {len(rows)}")
    return rows[0]


def parse_replay(layout: str, path: Path) -> dict[str, Any]:
    raw = path.read_bytes()
    lines = raw.decode("utf-8").splitlines()
    prefix = PREFIX.format(LAYOUTS[layout])
    complete = dict(FIELDS.findall(one_row(lines, prefix + "_COMPLETE ")))
    for name, expected in (("physicalPatterns", 256), ("submissions", 256),
                           ("uniqueLinks", 256), ("callbackUnits", 4096),
                           ("retainedUnits", 4096)):
        number(complete, name, expected)
    if layout != "native-big-grid":
        number(complete, "targetGrids", 16)
    if sum("All 1 required tests passed :)" in line for line in lines) != 1:
        raise ValueError("missing unique required GameTest success")
    if sum(line.startswith("BUILD SUCCESSFUL") for line in lines) != 1:
        raise ValueError("missing unique Gradle success")
    source = dict(FIELDS.findall(one_row(lines, "AE2F_SCALE_SOURCE_HOSTS ")))
    for name, expected in (("hosts", 16), ("providerNodes", 16), ("lanes", 16),
                           ("rootChannels", 16), ("gridChannels", 19), ("cpuChannels", 1),
                           ("driveChannels", 1)):
        number(source, name, expected)
    if layout != "native-big-grid":
        catalog_row = dict(FIELDS.findall(one_row(lines, prefix + "_CATALOG ")))
        for name, expected in (("physicalPatterns", 256), ("hosts", 16),
                               ("slotsPerHost", 16), ("submissions", 0)):
            number(catalog_row, name, expected)

    work = [dict(FIELDS.findall(line.split(prefix + " ", 1)[1]))
            for line in lines if prefix + " job=" in line]
    drive = [dict(FIELDS.findall(line.split("AE2F_SCALE_DRIVE ", 1)[1]))
             for line in lines if "AE2F_SCALE_DRIVE job=" in line]
    if len(work) != 256 or len(drive) != 256:
        raise ValueError(f"work/Drive cardinality: {len(work)}/{len(drive)}")
    catalog = []
    links: set[str] = set()
    inputs: set[str] = set()
    outputs: set[str] = set()
    host_owners: dict[int, tuple[str, ...]] = {}
    for index, (job, cell) in enumerate(zip(work, drive), 1):
        for name, expected in (("job", index), ("submissions", index), ("physicalPatterns", 256),
                               ("host", (index - 1) // 16), ("slot", (index - 1) % 16),
                               ("transitions", 16), ("callback", 16),
                               ("retainedUnits", index * 16)):
            number(job, name, expected)
        for name, expected in (("job", index), ("physicalSlot", (index - 1) % 16),
                               ("chestBefore", 16), ("chestAfter", 0),
                               ("driveAfter", 16), ("otherCells", 0),
                               ("retainedTypes", index), ("retainedUnits", index * 16)):
            number(cell, name, expected)
        job_id = job.get("jobId", "")
        if not re.fullmatch(r"[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}", job_id):
            raise ValueError(f"job {index}: missing native UUID")
        if job_id in links or job.get("inputKey") in inputs or job.get("outputKey") in outputs:
            raise ValueError(f"job {index}: duplicate link or typed key")
        links.add(job_id)
        inputs.add(job["inputKey"])
        outputs.add(job["outputKey"])
        if cell.get("outputKey") != job["outputKey"]:
            raise ValueError(f"job {index}: Drive output differs from callback")
        if any(job.get(field) != value for field, value in
               (("cpuIdle", "true"), ("returnsEmpty", "true"),
                ("sourceInput", "0"), ("chestOutput", "0"))):
            raise ValueError(f"job {index}: incomplete source CPU/return/input account")
        if layout != "native-big-grid":
            number(job, "targetInputBeforeExport", 16)
            number(job, "targetInput", 0)
        if layout == "federation" and job.get("route") != "ACTIVE":
            raise ValueError(f"job {index}: inactive route")
        owner_fields = ("machineOwner", "returnOwner") if layout == "native-big-grid" else (
            "targetGrid", "targetId", "machineOwner", "returnOwner")
        owner = tuple(job.get(field, "") for field in owner_fields)
        host = (index - 1) // 16
        if not all(owner) or (host in host_owners and owner != host_owners[host]):
            raise ValueError(f"job {index}: missing or changed physical target/return owner")
        host_owners[host] = owner
        catalog.append((job["inputKey"], job["outputKey"]))
    if not inputs.isdisjoint(outputs):
        raise ValueError("input/output catalog overlaps")
    if len(set(host_owners.values())) != 16:
        raise ValueError("sixteen distinct physical machine/return owners required")
    final = drive[-1]
    if final.get("cellTypes") != "63,63,63,63,4" or final.get("cellUnits") != "1008,1008,1008,1008,64":
        raise ValueError("final five-cell Drive readback differs")

    resource_rows = [dict(FIELDS.findall(line.split("AE2F_SCALE_RESOURCE_SNAPSHOT ", 1)[1]))
                     for line in lines if "AE2F_SCALE_RESOURCE_SNAPSHOT " in line]
    if len(resource_rows) > 1:
        raise ValueError("multiple resource snapshots in selected process")
    measured_resources = resource_rows[0] if resource_rows else None
    if measured_resources is not None and (measured_resources.get("layout") != layout
                                           or measured_resources.get("measurementPhase") != "postReplay"):
        raise ValueError("resource snapshot layout/phase does not match completed replay")

    topology_marker = prefix + ("_LAYOUT " if layout == "native-big-grid" else "_TOPOLOGY ")
    topology = one_row(lines, topology_marker)
    facts = dict(FIELDS.findall(topology))
    if layout == "native-big-grid":
        number(facts, "cpuCount", 1)
        number(facts, "mountedDriveCells", 5)
        number(facts, "gridChannels", 19)
        if facts.get("networkPowered") != "true":
            raise ValueError("direct source not powered")
        topology_description = {"sourceGrids": 1, "targetGrids": 0,
                                "targetCells": 0, "targetPowerSources": 0,
                                "physicalRoute": "direct machine adjacency"}
        selected_pod_chunk_keys = int(facts["loadedHostMachineChunks"])
    else:
        number(facts, "sourceCpu", 1)
        number(facts, "sourceDriveCells", 5)
        number(facts, "sourceEnergy", 1)
        number(facts, "targetCells", 16)
        number(facts, "targetEnergy", 16)
        number(facts, "targetMachines", 16)
        if facts.get("sourcePowered") != "true" or "false" in topology.split("targetPowered=", 1)[-1].split(" loadedPodChunks=", 1)[0]:
            raise ValueError("source or target not powered")
        topology_description = {"sourceGrids": 1, "targetGrids": 16,
                                "targetCells": 16, "targetPowerSources": 16,
                                "physicalRoute": "Interface" if layout == "native-subnet" else "Bridge/Endpoint"}
        selected_pod_chunk_keys = int(facts["loadedPodChunks"])
    if facts.get("sourceGrid") != source.get("grid"):
        raise ValueError("source Grid identity differs between source and layout receipts")
    return {
        "layout": layout,
        "receipt": {"path": str(path), "sha256": digest(raw)},
        "catalogSha256": digest(json.dumps(catalog, separators=(",", ":")).encode()),
        "inputReplaySha256": digest(json.dumps([entry[0] for entry in catalog], separators=(",", ":")).encode()),
        "physicalPatterns": len(catalog), "orderedJobs": len(work),
        "callbackUnits": 4096, "driveUnits": int(final["retainedUnits"]),
        "sourceCpu": 1, "sourceDriveCells": 5,
        "sourceGridUsedChannels": int(source["gridChannels"]),
        "topology": topology_description, "selectedPodChunkKeysNotWorldCount": selected_pod_chunk_keys,
        "measuredResources": measured_resources,
        "limitations": ["source CPU/Drive counts in subnet/Federation topology rows are fixture assertions",
                        "powered status is not finite energy debit",
                        "pod/host chunk-key sets are not whole-world loaded chunks or ticket counts"],
    }


def compare(ledgers: dict[str, dict[str, Any]], observations: dict[str, Any] | None = None) -> dict[str, Any]:
    reasons: list[str] = []
    if len(ledgers) != 3 or set(ledgers) != set(LAYOUTS):
        reasons.append("all three layout receipts are required")
    else:
        direct = ledgers["native-big-grid"]
        for layout, ledger in ledgers.items():
            for field in ("catalogSha256", "inputReplaySha256", "physicalPatterns", "orderedJobs",
                          "callbackUnits", "driveUnits", "sourceCpu", "sourceDriveCells"):
                if ledger[field] != direct[field]:
                    reasons.append(f"{layout}: {field} differs from direct")
    if observations is None:
        reasons.append("missing measured whole-world chunks/tickets, finite energy and capacity/provisioning observations")
    else:
        if observations.get("schemaVersion") != 1:
            reasons.append("resource observation schemaVersion must be 1")
        measured = observations.get("layouts", {})
        if not isinstance(measured, dict):
            measured = {}
            reasons.append("resource observations must contain three layout records")
        direct_observations = measured.get("native-big-grid", {})
        if not isinstance(direct_observations, dict):
            direct_observations = {}
        for layout in LAYOUTS:
            fields = measured.get(layout, {})
            if not isinstance(fields, dict):
                fields = {}
            replay = ledgers.get(layout, {})
            if fields.get("receiptSha256") != ledgers.get(layout, {}).get("receipt", {}).get("sha256"):
                reasons.append(f"{layout}: resource observation is not bound to this receipt")
            snapshot = replay.get("measuredResources")
            if snapshot is None:
                reasons.append(f"{layout}: no live resource snapshot in persisted process receipt")
            elif any(str(fields.get(field)) != snapshot.get(field) for field in (*CAPACITY_FIELDS,
                    "sourceCpu", "sourceDriveCells", "sourceGridUsedChannels")):
                reasons.append(f"{layout}: resource observations differ from live snapshot")
            if fields.get("measurementPhase") != "postReplay" or fields.get("energyStorageKind") != "finite" or (
                    snapshot is not None and snapshot.get("energyStorageKind") != "finite"):
                reasons.append(f"{layout}: finite energy source/debit observation missing")
            for field in CAPACITY_FIELDS:
                value = fields.get(field)
                if type(value) is not int or value < 0 or (field not in ("worldChunkTickets", "worldChunkTicketsPeak") and value == 0):
                    reasons.append(f"{layout}: {field} missing or unmeasured")
                elif field in EQUAL_FIELDS and layout != "native-big-grid" and value != direct_observations.get(field):
                    reasons.append(f"{layout}: {field} differs from direct")
            if fields.get("machineCount") != 16:
                reasons.append(f"{layout}: machineCount contradicts sixteen physical machine owners")
            for current, peak in (("worldLoadedChunks", "worldLoadedChunksPeak"),
                                  ("worldChunkTickets", "worldChunkTicketsPeak")):
                if type(fields.get(current)) is int and type(fields.get(peak)) is int and fields[peak] < fields[current]:
                    reasons.append(f"{layout}: {peak} is below observed {current}")
            for field, ledger_field in (("sourceCpu", "sourceCpu"), ("sourceDriveCells", "sourceDriveCells"),
                                        ("sourceGridUsedChannels", "sourceGridUsedChannels")):
                value = fields.get(field)
                if type(value) is not int or value <= 0:
                    reasons.append(f"{layout}: {field} missing or unmeasured")
                elif ledger_field and replay.get(ledger_field) is not None and value != replay[ledger_field]:
                    reasons.append(f"{layout}: {field} contradicts replay")
                elif layout != "native-big-grid" and value != direct_observations.get(field):
                    reasons.append(f"{layout}: {field} differs from direct")
    energy = None
    if observations is not None:
        recorded = observations.get("layouts")
        if isinstance(recorded, dict) and all(isinstance(recorded.get(layout), dict) for layout in LAYOUTS):
            energy = {layout: {field: recorded[layout].get(field) for field in OBSERVED_FIELDS}
                      for layout in LAYOUTS}
    return {"decision": "NON_COMPARABLE" if reasons else "COMPARABLE",
            "speedupEligible": not reasons, "reasons": reasons,
            "observedEnergyNotEqualized": energy,
            "topologyDisclosedNotEqualized": {layout: ledger["topology"] for layout, ledger in ledgers.items()}}


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate untimed Task 37 small replay resource integrity")
    for layout in LAYOUTS:
        parser.add_argument("--" + layout, type=Path, required=True, metavar="PROCESS_LOG")
    parser.add_argument("--observations", type=Path, help="independently measured resource observations, bound by receipt hashes")
    args = parser.parse_args()
    paths: dict[str, Path] = {layout: getattr(args, layout.replace("-", "_")) for layout in LAYOUTS}
    ledgers: dict[str, dict[str, Any]] = {}
    errors: list[str] = []
    tier_path = Path(__file__).resolve().parents[1] / "tests/benchmarks/scale/small.json"
    tier_declaration: dict[str, Any] | None = None
    try:
        tier_bytes = tier_path.read_bytes()
        parsed_tier = json.loads(tier_bytes)
        if (not isinstance(parsed_tier, dict) or parsed_tier.get("id") != "small"
                or parsed_tier.get("logicalPatterns") != 256 or parsed_tier.get("orderUnits") != 4096
                or parsed_tier.get("layouts") != list(LAYOUTS)):
            raise ValueError("small tier's declared catalog/order/layouts differ from this replay contract")
        tier_declaration = {"path": str(tier_path), "sha256": digest(tier_bytes),
                            "logicalPatterns": parsed_tier["logicalPatterns"],
                            "orderUnits": parsed_tier["orderUnits"]}
    except (OSError, UnicodeError, ValueError) as error:
        errors.append(f"invalid small tier declaration: {error}")
    for layout, path in paths.items():
        try:
            ledgers[layout] = parse_replay(layout, path)
        except (OSError, UnicodeError, ValueError, KeyError) as error:
            errors.append(f"{layout}: invalid persisted receipt: {error}")
    observations: dict[str, Any] | None = None
    if args.observations:
        try:
            parsed = json.loads(args.observations.read_text())
            if not isinstance(parsed, dict):
                raise ValueError("resource observations must be a JSON object")
            observations = parsed
        except (OSError, UnicodeError, ValueError) as error:
            errors.append(f"invalid resource observations: {error}")
    result = compare(ledgers, observations)
    result["reasons"] = errors + result["reasons"]
    if errors:
        result["decision"] = "NON_COMPARABLE"
        result["speedupEligible"] = False
    result["ledgers"] = ledgers
    result["tierDeclarationNotMeasurement"] = tier_declaration
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["speedupEligible"] else 1


if __name__ == "__main__":
    sys.exit(main())
