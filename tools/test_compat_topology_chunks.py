"""Runner configuration tests; these do not start servers or count as gameplay evidence."""
import unittest

from compat_run import playthrough_chunk_properties


class TopologyChunkTest(unittest.TestCase):
    def test_seed_ranges_are_disjoint_and_preserve_dimension_pins(self):
        prefix = 'ae2federation.playthrough.topology.'
        settings = {prefix + 'seedStart': '11', prefix + 'join': 'p2p'}
        chunks = [playthrough_chunk_properties(settings, 'playthrough-topology', i, 3) for i in range(4)]
        self.assertEqual([int(chunk[prefix + 'seedStart']) for chunk in chunks], [11, 14, 17, 20])
        self.assertTrue(all(chunk[prefix + 'seedCount'] == '3' for chunk in chunks))
        self.assertTrue(all(chunk[prefix + 'join'] == 'p2p' for chunk in chunks))
        self.assertEqual(settings, {prefix + 'seedStart': '11', prefix + 'join': 'p2p'})

    def test_method_alias_selects_seed_offsets(self):
        self.assertEqual(playthrough_chunk_properties({}, 'playthroughTopology', 2, 7), {
            'ae2federation.playthrough.topology.seedStart': '14',
            'ae2federation.playthrough.topology.seedCount': '7'})

    def test_phase_one_target_offsets_are_unchanged(self):
        prefix = 'ae2federation.playthrough.'
        self.assertEqual(playthrough_chunk_properties({prefix + 'skipTargets': '9'}, 'playthroughCrafting', 2, 40), {
            prefix + 'skipTargets': '89', prefix + 'maxTargets': '40'})


if __name__ == '__main__':
    unittest.main()
