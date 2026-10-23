"""Synthetic report tests only: no Minecraft execution or gameplay coverage is claimed."""
import contextlib
import io
import json
import pathlib
import tempfile
import unittest
from unittest import mock

import compat_transit_restart


class RestartConfirmationTest(unittest.TestCase):
    def test_passing_isolated_confirmation_preserves_failure_and_updates_both_reports(self):
        with tempfile.TemporaryDirectory() as temporary:
            first = pathlib.Path(temporary) / 'first'
            second = pathlib.Path(temporary) / 'second'
            for directory, passed in [(first, False), (second, True)]:
                directory.mkdir()
                result = {'pass': passed, 'invariant': '' if passed else 'return-ledger',
                          'reason': '' if passed else 'residual debt'}
                report = {'cases': [{'classification': 'both-pass' if passed else 'baseline-only',
                                     'federated': result}],
                          'byDimension': {'join=router-switch': {'baseline-only': 1}},
                          'byPerturbation': {'restart': {'baseline-only': 1}}}
                (directory / 'playthrough-topology.json').write_text(json.dumps(report))
                (directory / 'playthrough-topology.md').write_text(
                    '|baseline-only|not-required|return-ledger: residual debt|\n')
            arguments = ['compat_transit_restart.py', '--no-build', '--accept-eula', '--playthrough-topology']
            with mock.patch('sys.argv', arguments), mock.patch.object(
                    compat_transit_restart, 'run_phases', side_effect=[first, second]) as phases, contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(compat_transit_restart.main(), 0)
            self.assertEqual(phases.call_count, 2)
            report = json.loads((first / 'playthrough-topology.json').read_text())
            row = report['cases'][0]
            self.assertEqual(row['classification'], 'flaky')
            self.assertFalse(row['firstAttempt']['pass'])
            self.assertTrue(row['confirmation']['pass'])
            self.assertEqual(row['federated'], row['confirmation'])
            self.assertEqual(report['byPerturbation']['restart'], {'flaky': 1})
            self.assertEqual(report['byDimension']['join=router-switch'], {'flaky': 1})
            markdown = (first / 'playthrough-topology.md').read_text()
            self.assertIn('|flaky|flaky|: |', markdown)
            self.assertIn('Both complete attempt results are retained in JSON', markdown)

    def test_clean_but_lost_job_is_confirmed_and_never_both_pass(self):
        with tempfile.TemporaryDirectory() as temporary:
            first = pathlib.Path(temporary) / 'first'
            second = pathlib.Path(temporary) / 'second'
            for directory in (first, second):
                directory.mkdir()
                report = {'cases': [{'classification': 'job-lost-on-reload',
                                     'federated': {'pass': True, 'invariant': '', 'reason': ''}}],
                          'byDimension': {'join=router-switch': {'job-lost-on-reload': 1}},
                          'byPerturbation': {'restart': {'job-lost-on-reload': 1}}}
                (directory / 'playthrough-topology.json').write_text(json.dumps(report))
                (directory / 'playthrough-topology.md').write_text('|job-lost-on-reload|not-required|: |\n')
            arguments = ['compat_transit_restart.py', '--no-build', '--accept-eula', '--playthrough-topology']
            with mock.patch('sys.argv', arguments), mock.patch.object(
                    compat_transit_restart, 'run_phases', side_effect=[first, second]) as phases, contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(compat_transit_restart.main(), 0)
            self.assertEqual(phases.call_count, 2)
            row = json.loads((first / 'playthrough-topology.json').read_text())['cases'][0]
            self.assertEqual(row['classification'], 'job-lost-on-reload')
            self.assertEqual(row['repeatability'], 'reproduced-invariant')
            self.assertEqual(row['confirmationClassification'], 'job-lost-on-reload')


if __name__ == '__main__':
    unittest.main()
