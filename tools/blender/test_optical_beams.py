"""Optical continuity and containment checks on actual generated geometry."""
import math
import unittest

from optical_beams import PROFILES, generate, packet


class OpticalBeamTests(unittest.TestCase):
    def test_neighboring_straight_blocks_share_identical_boundary(self):
        for key in PROFILES:
            for coordinate in (-3, 0, 2):
                for cycle in (0, .27, .73):
                    left = generate(key, 3, (coordinate, 0, 0), (coordinate, 0, 0), cycle)
                    right = generate(key, 3, (coordinate + 1, 0, 0), (coordinate + 1, 0, 0), cycle)
                    def boundary(mesh):
                        return {(round(y, 9), round(z, 9)) for x, y, z in mesh.vertices
                                if abs(x - coordinate - 1) < 1e-9}
                    self.assertTrue(boundary(left))
                    self.assertEqual(boundary(left), boundary(right), (key, coordinate, cycle))

    def test_cycle_and_topology(self):
        for key in PROFILES:
            for mask in (3, 12, 48, 19, 63):
                first = generate(key, mask, (0, 0, 0), (0, 0, 0), 0)
                for cycle in (.25, 1):
                    other = generate(key, mask, (0, 0, 0), (0, 0, 0), cycle)
                    self.assertEqual(first.faces, other.faces)
                    self.assertEqual(first.materials, other.materials)
                    if cycle == 1:
                        self.assertLess(max(abs(a-b) for p, q in zip(first.vertices, other.vertices)
                                            for a, b in zip(p, q)), 1e-9)

    def test_light_stays_inside_narrow_axial_envelope(self):
        for key in PROFILES:
            for cycle in (0, .23, .58):
                mesh = generate(key, 3, (0, 0, 0), (0, 0, 0), cycle)
                for x, y, z in mesh.vertices:
                    self.assertTrue(all(math.isfinite(v) for v in (x, y, z)))
                    self.assertTrue(0 <= x <= 1)
                    self.assertLessEqual(math.hypot(y + .5, z - .5), .086)

    def test_envelope_moves_in_positive_direction_without_boundary_restart(self):
        for cycle in (0, .25, .6):
            self.assertAlmostEqual(packet(2 * cycle, cycle), 1)
            self.assertAlmostEqual(packet(2 * cycle + 1, cycle), 0)


if __name__ == '__main__':
    unittest.main()
