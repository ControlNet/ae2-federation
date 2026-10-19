"""Checks of the chosen R beam at game precision, its junction paths and the pieces around them, without Blender."""
import math
import unittest

from bundle_beams import SOLID_LIMIT
from plasma_beam import (SHELLS, VARIANTS, elbow, generate, quads_per_block, ring_quads, run_paths, shell_tiles,
                         straight)

SCENES = ('straight', 'branch', 'turn')


def distance_to_paths(point, paths):
    return min(math.dist(point, s.point) for samples in paths for s in samples)


class PlasmaBeamTests(unittest.TestCase):
    def test_every_variant_loops_with_stable_topology(self):
        for variant in VARIANTS:
            for scene in SCENES:
                paths = run_paths(variant, scene)
                first = generate(variant, .6, 0, paths)
                later = generate(variant, .6, .4, paths)
                self.assertEqual(first.faces, later.faces, (variant, scene))
                last = generate(variant, .6, 1, paths)
                drift = max(abs(a - b) for p, q in zip(first.vertices, last.vertices) for a, b in zip(p, q))
                self.assertLess(drift, 1e-9, (variant, scene))

    def test_lobes_repeat_with_the_games_two_block_clock(self):
        # The game's phase wraps every 2 blocks: a beam 2 blocks on must have the same shape.
        for variant in VARIANTS:
            one = generate(variant, .6, .3, [straight(0, .5, 1.5, (0, 1, 1), VARIANTS[variant][2])])
            two = generate(variant, .6, .3, [straight(0, 2.5, 3.5, (0, 1, 1), VARIANTS[variant][2])])
            for p, q in zip(one.vertices, two.vertices):
                self.assertAlmostEqual(p[0] + 2, q[0], places=9)
                self.assertAlmostEqual(p[1], q[1], places=9)
                self.assertAlmostEqual(p[2], q[2], places=9)

    def test_solid_core_fits_the_ring_bore_everywhere(self):
        for variant in VARIANTS:
            for scene in SCENES:
                paths = run_paths(variant, scene)
                for load in (0, .6, 1, 1.25):
                    mesh = generate(variant, load, .25, paths)
                    for face, index in zip(mesh.faces, mesh.indices):
                        if mesh.materials[index][3] != 'solid':
                            continue
                        for i in face:
                            self.assertLessEqual(distance_to_paths(mesh.vertices[i], paths), SOLID_LIMIT + 1e-9)

    def test_overload_turns_red(self):
        mesh = generate('ship_soft', 1.25, 0, run_paths('ship_soft', 'straight'))
        self.assertTrue(all(rgb[0] == 1 for _, rgb, _, _ in mesh.materials))

    def test_elbow_meets_both_arms(self):
        samples = elbow((3, 1, 1), 0, -1, 1, 1, 2, 16)
        self.assertEqual((2, 1, 1), samples[0].point)
        self.assertEqual((3, 2, 1), tuple(round(c, 9) for c in samples[-1].point))
        self.assertEqual((0, 1), (samples[0].mix, samples[-1].mix))

    def test_shipping_cost_fits_four_cells_of_ordinary_budget(self):
        # A 2x2 layer draws one beam where four ordinary cells would draw four, each within 1700 vertices.
        self.assertLessEqual(4 * quads_per_block('ship_soft'), 4 * 1700)
        self.assertLessEqual(4 * quads_per_block('ship_six'), 4 * 1700)
        self.assertGreater(4 * quads_per_block('approved'), 4 * 1700)

    def test_shells_close_only_where_a_piece_ends(self):
        for scene in SCENES:
            tiles = shell_tiles(scene)
            caps = sum(closed for piece in SHELLS[scene] for closed in piece[4:])
            side = 28 * 28
            lengths = sum(16 * (end - start) - 2 * (low + high) for _, start, end, _, low, high in SHELLS[scene])
            self.assertEqual(4 * 28 * lengths + caps * side, len(tiles), scene)

    def test_each_shell_piece_surrounds_its_beam(self):
        for scene in SCENES:
            paths = run_paths('ship_soft', scene)
            for axis, start, end, centre, _, _ in SHELLS[scene]:
                inside = [s.point for samples in paths for s in samples
                          if start < s.point[axis] < end
                          and all(abs(s.point[k] - centre[k]) < 1e-9 for k in range(3) if k != axis)]
                self.assertTrue(inside, (scene, axis, start))
            # Every beam sample outside a joint lies inside a shell piece's inner cross-section.
            for samples in paths:
                for s in samples:
                    covered = any(start <= s.point[axis] <= end and all(
                        abs(s.point[k] - centre[k]) < 14 / 16 for k in range(3) if k != axis)
                        for axis, start, end, centre, _, _ in SHELLS[scene])
                    in_joint = scene != 'straight' and 2 <= s.point[0] <= 4 and s.point[1] <= 2
                    self.assertTrue(covered or in_joint, (scene, s.point))
            # And the tiles are drawn round those axis lines, 14 voxels out.
            for quad, _ in shell_tiles(scene):
                middle = [sum(p[k] for p in quad) / 4 for k in range(3)]
                self.assertTrue(any(start <= middle[axis] <= end and all(
                    abs(middle[k] - centre[k]) <= 14 / 16 for k in range(3) if k != axis)
                    for axis, start, end, centre, _, _ in SHELLS[scene]), (scene, middle))

    def test_rings_stay_inside_their_scenes(self):
        for scene in SCENES:
            for points, uvs in ring_quads(scene):
                for x, y, z in points:
                    self.assertTrue(0 <= x <= 8 and 0 <= y <= 6 and 0 <= z <= 2, (scene, x, y, z))


if __name__ == '__main__':
    unittest.main()
