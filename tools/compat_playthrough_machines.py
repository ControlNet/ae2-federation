"""Validate complete chapter chunks and join the two machine paths by quest task identity."""
import argparse
import json
from pathlib import Path

from compat_playthrough import atomic_write, merge


def combine(directories):
    groups = {}
    for directory in directories:
        report = json.loads((directory / 'playthrough-machines.json').read_text())
        crafting = json.loads((directory / 'playthrough-crafting.json').read_text())
        for row in report['targets']:
            for layout in ('baseline', 'federated'):
                result = row.get(layout)
                if result and result['pass'] and result['invariants'].get('machine-idle-empty') != 'PASS':
                    raise ValueError(f'Missing machine buffer evidence: {row["taskId"]}/{layout}')
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
            if projection.get('machineSteps') != endpoint.get('machineSteps'):
                raise ValueError(f'Machine recipe selections differ for task {task}')
            rows.append(dict(chapter=projection['chapter'], chapterFilter=chapter, taskId=task, item=projection['item'],
                             machineSteps=projection.get('machineSteps', {}),
                             paths={'projection': projection, 'endpoint': endpoint}))
    return dict(completed=True, chapters=chapters, targets=rows)


def markdown(report):
    lines = ['# Machine playthrough', '',
             'Each path compares a fresh plain AE2 control with the federated layout. Full evidence is in JSON.', '',
             '| Chapter | Item | Task | Projection | Endpoint | Real machine recipes |',
             '|---|---|---|---|---|---|']
    for row in report['targets']:
        values = [row['chapter'], row['item'], row['taskId'],
                  *[row['paths'][path]['classification'] for path in ('projection', 'endpoint')], row['machineSteps']]
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
