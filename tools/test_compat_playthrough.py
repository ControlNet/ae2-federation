"""Synthetic report corruption cases; these fixtures are not gameplay evidence."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from compat_playthrough import CLASSES, atomic_write, merge


class PlaythroughReportsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        stock = {'materials': {}, 'factory': {}, 'requester': {'minecraft:stone': 1}}
        result = dict(pass_=True, inventories=stock, expectedInventories=copy.deepcopy(stock),
                      invariants={'cell-inventories': 'PASS'}, usedItems={}, extracted={},
                      ledger={'owed': [], 'transit': []}, cleanup={'verifiedAfterDelay': True},
                      cpuBytes=1, planBytes=1, signatures={'recipe': {'outputs': {'minecraft:stone': 1}, 'remainders': {}}})
        result['pass'] = result.pop('pass_')
        self.report = dict(schemaVersion=3, mode='crafting-frontier', chapterFilter='test', itemFilter=[],
                           targetTickBudget=1200, ticksPerCraft=20, assemblerCopies=28,
                           machineBackedRecipeClasses=[], sprint=True, completed=True, harnessError='',
                           skipTargets=0, eligibleTargetCount=1, elapsedSeconds=1, deferred=[],
                           targets=[dict(taskId='task1', chapter='test', classification='both-pass',
                                         baseline=copy.deepcopy(result), federated=copy.deepcopy(result))])

    def directory(self, name, report):
        path = self.root / name
        path.mkdir()
        (path / 'playthrough-crafting.json').write_text(json.dumps(report))
        return path

    def test_complete_disjoint_chunks(self):
        first, second = copy.deepcopy(self.report), copy.deepcopy(self.report)
        first['eligibleTargetCount'] = second['eligibleTargetCount'] = 2
        first['deferred'] = [{'task': 'task2'}]
        second['deferred'] = [{'task': 'task1'}]
        second['skipTargets'] = 1
        second['targets'][0]['taskId'] = 'task2'
        report = merge([self.directory('second', second), self.directory('first', first)])
        self.assertEqual([row['taskId'] for row in report['targets']], ['task1', 'task2'])
        self.assertEqual(report['overall'], dict.fromkeys(CLASSES, 0) | {'both-pass': 2})

    def test_rejects_missing_and_duplicate_tasks(self):
        path = self.directory('one', self.report)
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            merge([path, path])
        self.report['eligibleTargetCount'] = 2
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            merge([self.directory('missing', self.report)])

    def test_rejects_stranded_item_debt_transit_and_signature(self):
        for corruption in ('item', 'owed', 'transit', 'signature'):
            with self.subTest(corruption=corruption):
                report = copy.deepcopy(self.report)
                result = report['targets'][0]['federated']
                if corruption == 'item':
                    result['inventories']['factory']['minecraft:bucket'] = 3
                elif corruption == 'signature':
                    result['signatures']['recipe']['remainders']['minecraft:bucket'] = 3
                else:
                    result['ledger'][corruption].append('unexpected component-bearing key')
                with self.assertRaises(ValueError):
                    merge([self.directory(corruption, report)])

    def test_failed_atomic_replace_preserves_checkpoint(self):
        path = self.root / 'checkpoint.json'
        path.write_text('{"nextEligibleOffset": 7}')
        with patch('compat_playthrough.os.replace', side_effect=OSError('injected rename failure')):
            with self.assertRaises(OSError):
                atomic_write(path, '{"nextEligibleOffset": 8}')
        self.assertEqual(json.loads(path.read_text())['nextEligibleOffset'], 7)
        self.assertEqual(list(self.root.iterdir()), [path])


if __name__ == '__main__':
    unittest.main()
