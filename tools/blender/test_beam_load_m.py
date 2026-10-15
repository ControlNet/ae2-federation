"""Verify the real M load generator retains straight optical surfaces and seams."""
import unittest

from beam_load_m import generate_for_load


class CoherentLoadTests(unittest.TestCase):
    def test_straight_surfaces_are_axially_centered(self):
        for ratio in (0, .25, .6, 1, 1.25):
            mesh = generate_for_load(ratio, 3, (0, 0, 0), (0, 0, 0), .25)
            self.assertFalse({3, 4}.intersection(mesh.materials))
            rings = {}
            for x, y, z in mesh.vertices:
                rings.setdefault(round(x, 9), set()).add((round(y + .5, 9), round(z - .5, 9)))
                self.assertLessEqual((y + .5)**2 + (z - .5)**2, .08**2)
            for ring in rings.values():
                self.assertEqual(ring, {(-y, -z) for y, z in ring})

    def test_negative_coordinate_seams(self):
        for ratio in (0, .6, 1.25):
            for cycle in (0, .37):
                left = generate_for_load(ratio, 3, (-1, 0, 0), (-1, 0, 0), cycle)
                right = generate_for_load(ratio, 3, (0, 0, 0), (0, 0, 0), cycle)
                def seam(mesh):
                    return {(round(y, 9), round(z, 9)) for x, y, z in mesh.vertices if abs(x) < 1e-9}
                self.assertTrue(seam(left))
                self.assertEqual(seam(left), seam(right))

    def test_load_topology_and_cycle_closure(self):
        for mask in (3, 12, 48, 19, 63):
            base = generate_for_load(0, mask, (0, 0, 0), (0, 0, 0), 0)
            for ratio in (.6, 1.25):
                first = generate_for_load(ratio, mask, (0, 0, 0), (0, 0, 0), 0)
                last = generate_for_load(ratio, mask, (0, 0, 0), (0, 0, 0), 1)
                self.assertEqual(base.faces, first.faces)
                self.assertEqual(base.materials, first.materials)
                self.assertLess(max(abs(a-b) for p, q in zip(first.vertices, last.vertices)
                                    for a, b in zip(p, q)), 1e-9)


if __name__ == '__main__':
    unittest.main()
