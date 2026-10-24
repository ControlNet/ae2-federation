"""Checks of the 2x2 middle-beam concepts and the shell and rings they are judged in, without Blender."""
import math
import unittest

from bundle_beams import (LENGTH, LOADS, PROFILES, RING_OPENING, SOLID_LIMIT, generate, ring_boundaries, ring_quads,
                          shell_tiles, solid_reach)


class BundleBeamTests(unittest.TestCase):
    def test_concepts_have_valid_distinct_geometry(self):
        signatures = set()
        for key in PROFILES:
            mesh = generate(key, .6, .25)
            self.assertTrue(mesh.vertices, key)
            self.assertEqual(len(mesh.faces), len(mesh.indices))
            self.assertTrue(all(math.isfinite(v) for point in mesh.vertices for v in point), key)
            self.assertTrue(all(0 <= i < len(mesh.vertices) for face in mesh.faces for i in face), key)
            self.assertTrue(all(0 <= i < len(mesh.materials) for i in mesh.indices), key)
            self.assertTrue(all(0 <= alpha <= 1 and mode in ('solid', 'add')
                                for _, _, alpha, mode in mesh.materials), key)
            signatures.add(tuple(mesh.vertices))
        self.assertEqual(len(PROFILES), len(signatures))

    def test_animation_keeps_topology_and_loops(self):
        for key in PROFILES:
            for load in (0, .6, 1.25):
                first = generate(key, load, 0)
                for cycle in (.125, .5, .9375):
                    later = generate(key, load, cycle)
                    self.assertEqual(first.faces, later.faces, (key, load, cycle))
                    self.assertEqual(first.indices, later.indices, (key, load, cycle))
                last = generate(key, load, 1)
                drift = max(abs(a - b) for p, q in zip(first.vertices, last.vertices) for a, b in zip(p, q))
                self.assertLess(drift, 1e-9, (key, load))

    def test_solid_light_passes_through_the_ring_opening(self):
        self.assertLess(SOLID_LIMIT, RING_OPENING)
        for key in PROFILES:
            for load in (*LOADS.values(), 1.25):
                for cycle in (0, .25, .5, .75):
                    self.assertLessEqual(solid_reach(generate(key, load, cycle)), SOLID_LIMIT, (key, load, cycle))

    def test_new_concepts_are_never_a_hairline(self):
        base = solid_reach(generate('base', 0, 0))
        for key in PROFILES:
            if key == 'base':
                continue
            # Idle is where the shipped beam reads thinnest; every concept starts at least as wide as it.
            self.assertGreaterEqual(solid_reach(generate(key, 0, 0)), base, key)
            self.assertGreater(solid_reach(generate(key, .6, 0)), 2 * solid_reach(generate('base', .6, 0)) - .06, key)

    def test_beam_stays_inside_the_shell(self):
        for key in PROFILES:
            for x, y, z in generate(key, 1, .3).vertices:
                self.assertTrue(0 < x < LENGTH, key)
                self.assertLess(abs(y - 1), 1 - 2 / 16, key)
                self.assertLess(abs(z - 1), 1 - 2 / 16, key)

    def test_shell_covers_the_inset_box_and_glass_is_the_middle_texel(self):
        tiles = shell_tiles()
        area = sum(abs(q[0][0] - q[2][0]) * abs(q[0][1] - q[2][1]) + abs(q[0][1] - q[2][1]) * abs(q[0][2] - q[2][2])
                   + abs(q[0][0] - q[2][0]) * abs(q[0][2] - q[2][2]) for q, _ in tiles)
        side, along = 28 / 16, LENGTH - 4 / 16
        self.assertAlmostEqual(4 * side * along + 2 * side * side, area)
        texels = {t for _, t in tiles}
        self.assertIn((6, 6), texels)
        self.assertIn((2, 2), texels)
        self.assertTrue(all(2 <= u <= 4 or (u, v) == (6, 6) for u, v in texels))
        self.assertEqual(len(tiles) - 28 * 28, len(shell_tiles(open_end=True)))

    def test_rings_stand_on_their_boundaries_round_the_axis(self):
        self.assertEqual([2, 4], ring_boundaries())
        quads = ring_quads([2])
        xs = [p[0] for q, _ in quads for p in q]
        self.assertAlmostEqual(2, (min(xs) + max(xs)) / 2)
        for q, uvs in quads:
            for _, y, z in q:
                self.assertLessEqual(abs(y - 1), 1)
                self.assertLessEqual(abs(z - 1), 1)
            self.assertTrue(all(0 <= u <= 1 and 0 <= v <= 1 for u, v in uvs))
        # The opening: no ring face comes nearer the axis than the bore the flanges leave.
        nearest = min(max(abs(sum(p[1] for p in q) / 4 - 1), abs(sum(p[2] for p in q) / 4 - 1)) for q, _ in quads)
        self.assertGreaterEqual(nearest + 1e-9, RING_OPENING)


if __name__ == '__main__':
    unittest.main()
