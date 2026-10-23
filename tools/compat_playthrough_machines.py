"""Validate complete chapter chunks and join the two machine paths by quest task identity."""
import argparse
import json
from pathlib import Path

from compat_playthrough import atomic_write, merge


LAYOUTS = ('baseline', 'federated')


def planned_runs(row, layout):
    result = row.get(layout)
    return result['cleanup'].get('machineRuns') if result else None


def check_observations(row):
    """Observed steps must follow from a passing layout's recorded plan runs, never from the proof alone."""
    steps = row.get('machineSteps', {})
    for layout in LAYOUTS:
        result = row.get(layout)
        if result and result['pass'] and result['invariants'].get('machine-idle-empty') != 'PASS':
            raise ValueError(f'Missing machine buffer evidence: {row["taskId"]}/{layout}')
        if result and result['pass'] and steps and planned_runs(row, layout) is None:
            raise ValueError(f'Missing machine run evidence: {row["taskId"]}/{layout}')
    for step, entry in steps.items():
        expected = [layout for layout in LAYOUTS if row.get(layout) and row[layout]['pass']
                    and (planned_runs(row, layout) or {}).get(step, 0) > 0]
        recorded = {layout: (planned_runs(row, layout) or {}).get(step) for layout in LAYOUTS if planned_runs(row, layout) is not None}
        if entry.get('observedIn') != expected or any(entry['runs'].get(layout) != runs for layout, runs in recorded.items()):
            raise ValueError(f'Machine observation differs from recorded runs: {row["taskId"]}/{step}')
    if sorted(row.get('realMachineSteps', [])) != sorted(step for step, entry in steps.items() if entry['observedIn']):
        raise ValueError(f'Real machine steps differ from observations: {row["taskId"]}')


def definitions(row):
    return {step: {key: value for key, value in entry.items() if key not in ('runs', 'observedIn')}
            for step, entry in row.get('machineSteps', {}).items()}


def coverage(rows, path):
    """Per adapter: tasks with an observed step and distinct observed recipes on this path."""
    tasks, recipes = {}, {}
    for row in rows:
        report = row['paths'][path]
        for step in report.get('realMachineSteps', []):
            adapter = report['machineSteps'][step]['adapter']
            tasks.setdefault(adapter, set()).add(row['taskId'])
            recipes.setdefault(adapter, set()).add(step)
    return {adapter: dict(tasks=len(tasks[adapter]), recipes=len(recipes[adapter])) for adapter in sorted(tasks)}


def combine(directories):
    groups = {}
    for directory in directories:
        report = json.loads((directory / 'playthrough-machines.json').read_text())
        crafting = json.loads((directory / 'playthrough-crafting.json').read_text())
        for row in report['targets']:
            check_observations(row)
        if report != crafting:
            raise ValueError(f'Machine/crafting checkpoints differ in {directory}')
        key = (report['chapterFilter'], report['machinePath'])
        groups.setdefault(key, []).append(directory)
    chapters = {}
    for (chapter, path), paths in groups.items():
        reports = [json.loads((p / 'playthrough-machines.json').read_text()) for p in paths]
        if any((r['machineBackedRecipeClasses'], r.get('machineCopies')) !=
               (reports[0]['machineBackedRecipeClasses'], reports[0].get('machineCopies')) for r in reports):
            raise ValueError('Machine adapter selections differ')
        chapters.setdefault(chapter, {})[path] = merge(paths)
    rows = []
    for chapter, paths in chapters.items():
        if set(paths) != {'projection', 'endpoint'}:
            raise ValueError(f'Missing machine path for chapter {chapter}')
        if any(paths[path]['machineBackedRecipeClasses'] != paths['projection']['machineBackedRecipeClasses']
               or paths[path].get('machineCopies') != paths['projection'].get('machineCopies') for path in paths):
            raise ValueError(f'Machine settings differ between paths for chapter {chapter}')
        indexed = {path: {row['taskId']: row for row in report['targets']} for path, report in paths.items()}
        if indexed['projection'].keys() != indexed['endpoint'].keys():
            raise ValueError(f'Task sets differ between paths for chapter {chapter}')
        for task, projection in indexed['projection'].items():
            endpoint = indexed['endpoint'][task]
            if definitions(projection) != definitions(endpoint):
                raise ValueError(f'Machine recipe selections differ for task {task}')
            # Both paths share the same plain AE2 control, so its planned machine runs must agree.
            controls = [planned_runs(row, 'baseline') for row in (projection, endpoint)]
            if None not in controls and controls[0] != controls[1]:
                raise ValueError(f'Plain AE2 control machine runs differ between paths for task {task}')
            rows.append(dict(chapter=projection['chapter'], chapterFilter=chapter, taskId=task, item=projection['item'],
                             machineSteps=definitions(projection),
                             realMachineSteps={path: indexed[path][task].get('realMachineSteps', []) for path in paths},
                             paths={'projection': projection, 'endpoint': endpoint}))
    return dict(completed=True, chapters=chapters, targets=rows,
                adapterCoverage={path: coverage(rows, path) for path in ('projection', 'endpoint')})


def markdown(report):
    lines = ['# Machine playthrough', '',
             'Each path compares a fresh plain AE2 control with the federated layout. Real machine recipes ran in a',
             'passing layout with planned runs above zero. Full evidence is in JSON.', '',
             '| Path | Adapter | Tasks | Recipes |', '|---|---|---:|---:|']
    for path, adapters in report['adapterCoverage'].items():
        lines += [f'|{path}|{adapter}|{counts["tasks"]}|{counts["recipes"]}|' for adapter, counts in adapters.items()]
    lines += ['', '| Chapter | Item | Task | Projection | Endpoint | Real machine recipes (projection / endpoint) |',
              '|---|---|---|---|---|---|']
    for row in report['targets']:
        values = [row['chapter'], row['item'], row['taskId'],
                  *[row['paths'][path]['classification'] for path in ('projection', 'endpoint')],
                  ' / '.join(str(row['realMachineSteps'][path]) for path in ('projection', 'endpoint'))]
        lines.append('|' + '|'.join(str(v).replace('|', '\\|').replace('\n', ' ') for v in values) + '|')
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path('build/playthrough'))
    parser.add_argument('directories', type=Path, nargs='+')
    args = parser.parse_args()
    report = combine(args.directories)
    args.output.mkdir(parents=True, exist_ok=True)
    atomic_write(args.output / 'playthrough-machines.md', markdown(report))
    atomic_write(args.output / 'playthrough-machines.json', json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f"Validated {len(report['targets'])} tasks across both machine paths")


if __name__ == '__main__':
    main()
