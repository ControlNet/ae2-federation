"""Checks of the 1x1 cable beam concepts against the cable's glass, the game's clock and each other, without Blender."""
import unittest

from cable_beams import LOADS, PROFILES, WINDOW, generate, glow_reach, quads_per_block, solid_reach

# A run of three cells along X, the middle one a joint with a branch south, and the branch cell; origins as the
# Blender scene places cells, one block apart.
RUN = [(3, (-1, 0, 0), (-1, -3, 0)), (19, (0, 0, 0), (0, -3, 0)), (3, (1, 0, 0), (1, -3, 0)),
       (32, (0, 0, 1), (0, -4, 0))]


class CableBeamTests(unittest.TestCase):
    def test_every_concept_keeps_its_light_inside_the_glass(self):
        for profile in PROFILES:
            for load in LOADS.values():
                for cycle in (0, .3, .7):
                    mesh = generate(profile, load, cycle, RUN)
                    self.assertLessEqual(solid_reach(mesh, RUN), WINDOW, (profile, load, cycle))
                    self.assertLessEqual(glow_reach(mesh, RUN), WINDOW + 1e-9, (profile, load, cycle))

    def test_topology_is_stable_so_the_loop_can_be_animated(self):
        for profile in PROFILES:
            first = generate(profile, .6, 0, RUN)
            later = generate(profile, .6, .45, RUN)
            self.assertEqual(first.faces, later.faces, profile)
            self.assertEqual(first.indices, later.indices, profile)
            last = generate(profile, .6, 1, RUN)
            drift = max(abs(a - b) for p, q in zip(first.vertices, last.vertices) for a, b in zip(p, q))
            self.assertLess(drift, 1e-9, profile)

    def test_cells_meet_without_a_seam(self):
        # Where two cells of a run meet, both draw the same outline at the shared face.
        for profile in PROFILES:
            if profile == 'm_current':
                continue
            left = generate(profile, .6, .3, [RUN[0]])
            right = generate(profile, .6, .3, [(3, (0, 0, 0), (0, -3, 0))])
            at = lambda mesh, x: sorted((round(v[1], 6), round(v[2], 6)) for v in mesh.vertices if abs(v[0] - x) < 1e-9)
            self.assertEqual(at(left, 0), at(right, 0), profile)

    def test_r_follows_the_two_block_clock(self):
        one = generate('r_fill', .6, .3, [(3, (0, 0, 0), (0, 0, 0))])
        two = generate('r_fill', .6, .3, [(3, (2, 0, 0), (0, 0, 0))])
        three = generate('r_fill', .6, .3, [(3, (1, 0, 0), (0, 0, 0))])
        drift = lambda a, b: max(abs(p - q) for u, v in zip(a.vertices, b.vertices) for p, q in zip(u, v))
        self.assertLess(drift(one, two), 1e-9)
        self.assertGreater(drift(one, three), 1e-3)

    def test_concepts_differ_in_size_and_cost(self):
        reach = {p: glow_reach(generate(p, .6, .3, RUN), RUN) for p in PROFILES}
        self.assertLess(reach['r_scaled'], reach['r_fill'])
        self.assertLess(quads_per_block('r_lite'), quads_per_block('r_fill'))
        self.assertIsNone(quads_per_block('m_current'))


if __name__ == '__main__':
    unittest.main()
