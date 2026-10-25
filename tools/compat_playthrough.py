"""Merge disjoint playthrough chunks and validate their complete accounting evidence.

    python3 tools/compat_playthrough.py --output build/playthrough runs/chunk1 runs/chunk2
JSON is authoritative. Both output files carry the same checkpoint ID.
"""
import argparse
import json
import os
import tempfile
import uuid
from pathlib import Path

CLASSES = ('both-pass', 'baseline-only', 'both-fail', 'federated-only', 'not-bootstrappable', 'nondeterministic')
SETTINGS = ('schemaVersion', 'mode', 'chapterFilter', 'itemFilter', 'targetTickBudget', 'ticksPerCraft',
            'assemblerCopies', 'machineBackedRecipeClasses', 'sprint')


def atomic_write(path, text):
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix=path.name, suffix='.tmp')
    try:
        with os.fdopen(fd, 'w') as stream:
            stream.write(text)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def merge(directories):
    reports = [json.loads((directory / 'playthrough-crafting.json').read_text()) for directory in directories]
    if not reports:
        raise ValueError('No chunks')
    for report in reports:
        if not report['completed'] or report['harnessError']:
            raise ValueError('Unfinished or broken harness chunk')
        if any(report[key] != reports[0][key] for key in SETTINGS):
            raise ValueError('Chunk settings differ')
    reports.sort(key=lambda report: report['skipTargets'])
    rows = [row for report in reports for row in report['targets']]
    identities = {row['taskId'] for row in rows}
    known = identities | {row['task'] for report in reports for row in report['deferred']}
    if len(identities) != len(rows):
        raise ValueError('Duplicate task identities; single-target reruns must remain separate evidence')
    if identities != known or len(rows) != reports[0]['eligibleTargetCount']:
        raise ValueError(f'Incomplete chapter: {len(rows)} of {reports[0]["eligibleTargetCount"]}; missing {sorted(known - identities)}')
    overall = dict.fromkeys(CLASSES, 0)
    chapters = {}
    for row in rows:
        classification = row['classification']
        overall[classification] += 1
        chapters.setdefault(row['chapter'], dict.fromkeys(CLASSES, 0))[classification] += 1
        for layout in ('baseline', 'federated'):
            result = row.get(layout)
            if not result or not result['pass']:
                continue
            if (any(value != 'PASS' for value in result['invariants'].values())
                    or result['inventories'] != result['expectedInventories']
                    or result['usedItems'] != result['extracted']
                    or result['ledger']['owed'] or result['ledger']['transit']
                    or not result['cleanup'].get('verifiedAfterDelay')
                    or result['cpuBytes'] < result['planBytes']):
                raise ValueError(f'Invalid passing evidence: {row["taskId"]}/{layout}')
        if classification == 'both-pass':
            if row['baseline']['inventories'] != row['federated']['inventories'] or row['baseline']['signatures'] != row['federated']['signatures']:
                raise ValueError(f'Differential mismatch classified both-pass: {row["taskId"]}')
    result = dict(reports[0])
    result.update(checkpointId=str(uuid.uuid4()), targets=rows, overall=overall, chapters=chapters,
                  selectedTargetCount=len(rows), completedTargetCount=len(rows), maxTargets=0, skipTargets=0,
                  nextEligibleOffset=len(rows), nextTask={}, incompleteTarget={}, deferred=[],
                  elapsedSeconds=sum(report['elapsedSeconds'] for report in reports),
                  runs=[{'report': str(directory / 'playthrough-crafting.json')} for directory in directories],
                  oneSidedEvidence={key: [row for row in rows if row['classification'] == key]
                                    for key in ('baseline-only', 'federated-only')})
    return result


def markdown(report):
    def escape(value):
        return str(value).replace('|', '\\|').replace('\n', ' ')
    lines = ['# Quest crafting playthrough', '', f"Checkpoint: {report['checkpointId']}. JSON is authoritative.", '',
             '| Chapter | Both pass | Baseline only | Both fail | Federated only | Not bootstrappable | Nondeterministic |',
             '|---|---:|---:|---:|---:|---:|---:|']
    for name, counts in [*report['chapters'].items(), ('Overall', report['overall'])]:
        lines.append('|' + escape(name) + '|' + '|'.join(str(counts[key]) for key in CLASSES) + '|')
    lines += ['', '## One-sided evidence', '']
    for classification in ('baseline-only', 'federated-only'):
        matching = report['oneSidedEvidence'][classification]
        lines.append(f'- {classification}: {len(matching)} targets.')
        for row in matching:
            lines.append(f"  - `{row['item']}`, task `{row['taskId']}`: " + escape(json.dumps({k: row[k] for k in ('baseline', 'federated')})))
    lines += ['', '| Quest | Item | Count | Class | Baseline | Federated | Stand-ins |', '|---|---|---:|---|---|---|---|']
    for row in report['targets']:
        cells = [row['quest'], row['item'], row['count'], row['classification']]
        for layout in ('baseline', 'federated'):
            result = row.get(layout)
            if not result:
                cells.append(row['reason'])
            else:
                state = 'PASS' if result['pass'] else f"{result['invariant']}: {result['reason']}"
                cells.append(f"{result['ticks']} ticks / {result['tickBudget']} budget; {state}")
        cells.append(json.dumps(row['standIns'], ensure_ascii=False))
        lines.append('|' + '|'.join(map(escape, cells)) + '|')
    lines += ['', '## Own non-crafting task skips', '']
    for row in report['skippedNonCrafting']:
        lines.append(f"- {row['chapter']} / {row['questId']}: `{row['item']}`")
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directories', nargs='+', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    report = merge(args.directories)
    args.output.mkdir(parents=True, exist_ok=True)
    atomic_write(args.output / 'playthrough-crafting.md', markdown(report))
    atomic_write(args.output / 'playthrough-crafting.json', json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print('PASS complete task coverage, full inventories, signatures, ledger/transit and cleanup:', report['overall'])


if __name__ == '__main__':
    main()
