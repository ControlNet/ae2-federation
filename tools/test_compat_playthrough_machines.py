"""Synthetic report corruption tests only; these fixtures are not machine gameplay evidence."""
import copy
import json
import unittest

from compat_playthrough_machines import combine
import test_compat_playthrough as fixtures


class MachineReportsTest(unittest.TestCase):
    setUp = fixtures.PlaythroughReportsTest.setUp
    directory = fixtures.PlaythroughReportsTest.directory

    def machine_directory(self, name, path, change=None):
        report = copy.deepcopy(self.report)
        report.update(machinePath=path, machineCopies=4, machineBackedRecipeClasses=['ae2:charger'])
        report['targets'][0].update(item='ae2:charged_certus_quartz_crystal', realMachineSteps=['charging'],
                                    machineSteps={'charging': {'adapter': 'charger', 'runs': {'baseline': 2, 'federated': 2},
                                                               'observedIn': ['baseline', 'federated']}})
        for layout in ('baseline', 'federated'):
            report['targets'][0][layout]['invariants']['machine-idle-empty'] = 'PASS'
            report['targets'][0][layout]['cleanup']['machineRuns'] = {'charging': 2}
        if change:
            change(report)
        directory = self.directory(name, report)
        (directory / 'playthrough-machines.json').write_text(json.dumps(report))
        return directory

    def test_two_paths(self):
        paths = [self.machine_directory(path, path) for path in ('projection', 'endpoint')]
        report = combine(paths)
        self.assertTrue(report['completed'])
        self.assertEqual(set(report['targets'][0]['paths']), {'projection', 'endpoint'})

    def test_missing_path(self):
        with self.assertRaisesRegex(ValueError, 'Missing machine path'):
            combine([self.machine_directory('one', 'projection')])

    def test_different_machine_recipes(self):
        def other_adapter(report):
            report['targets'][0]['machineSteps']['charging']['adapter'] = 'inscriber'
        paths = [self.machine_directory('one', 'projection'), self.machine_directory('two', 'endpoint', other_adapter)]
        with self.assertRaisesRegex(ValueError, 'recipe selections differ'):
            combine(paths)

    def test_coverage_counts_only_observed_steps(self):
        paths = [self.machine_directory(path, path) for path in ('projection', 'endpoint')]
        self.assertEqual(combine(paths)['adapterCoverage']['endpoint'], {'charger': {'tasks': 1, 'recipes': 1}})

    def test_zero_run_step_is_not_real(self):
        def unrun(report):
            row = report['targets'][0]
            row['machineSteps']['charging'].update(runs={'baseline': 0, 'federated': 0}, observedIn=[])
            row['realMachineSteps'] = []
            for layout in ('baseline', 'federated'):
                row[layout]['cleanup']['machineRuns'] = {'charging': 0}
        paths = [self.machine_directory('one', 'projection', unrun), self.machine_directory('two', 'endpoint', unrun)]
        report = combine(paths)
        self.assertEqual(report['targets'][0]['realMachineSteps'], {'projection': [], 'endpoint': []})
        self.assertEqual(report['adapterCoverage']['projection'], {})

    def test_planned_step_cannot_claim_observation(self):
        def claimed(report):
            for layout in ('baseline', 'federated'):
                report['targets'][0][layout]['cleanup']['machineRuns'] = {'charging': 0}
        with self.assertRaisesRegex(ValueError, 'observation differs'):
            combine([self.machine_directory('one', 'projection', claimed)])

    def test_passing_layout_needs_run_evidence(self):
        def missing(report):
            del report['targets'][0]['federated']['cleanup']['machineRuns']
        with self.assertRaisesRegex(ValueError, 'machine run evidence'):
            combine([self.machine_directory('one', 'projection', missing)])

    def test_control_runs_must_match_across_paths(self):
        def more(report):
            row = report['targets'][0]
            row['baseline']['cleanup']['machineRuns'] = {'charging': 3}
            row['machineSteps']['charging']['runs']['baseline'] = 3
        paths = [self.machine_directory('one', 'projection'), self.machine_directory('two', 'endpoint', more)]
        with self.assertRaisesRegex(ValueError, 'control machine runs differ'):
            combine(paths)

    def test_machine_buffer_failure_cannot_pass(self):
        paths = [self.machine_directory('one', 'projection'), self.machine_directory('two', 'endpoint',
            lambda report: report['targets'][0]['federated']['invariants'].update({'machine-idle-empty': 'occupied'}))]
        with self.assertRaisesRegex(ValueError, 'machine buffer evidence'):
            combine(paths)


    def test_machine_settings_must_match_across_paths(self):
        paths = [self.machine_directory('one', 'projection'), self.machine_directory(
            'two', 'endpoint', lambda report: report.update(machineCopies=1))]
        with self.assertRaisesRegex(ValueError, 'Machine settings differ'):
            combine(paths)

    def test_rejects_mismatched_checkpoint_files(self):
        path = self.machine_directory('one', 'projection')
        filename = path / 'playthrough-machines.json'
        report = json.loads(filename.read_text())
        report['elapsedSeconds'] += 1
        filename.write_text(json.dumps(report))
        with self.assertRaisesRegex(ValueError, 'checkpoints differ'):
            combine([path])
