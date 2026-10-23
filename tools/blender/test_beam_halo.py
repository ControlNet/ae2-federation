"""Geometry acceptance checks for the real aperture's clearance and animation."""
import math
import itertools
from assets import rotate
import json
from pathlib import Path
import unittest
from beam_halo import CONCEPTS, generate, palette, portal


class HaloTests(unittest.TestCase):
    def test_off_and_baseline(self):
        for key in CONCEPTS:
            self.assertEqual(generate(key, .6, 0, False), [])
        self.assertEqual(generate('BASE', 1, 0), [])

    def test_clearance(self):
        for key in ('B', 'D', 'F'):
            for frame in range(32):
                for quad, alpha in generate(key, 1, frame / 32):
                    self.assertTrue(0 <= alpha <= 1)
                    for x, y, z in quad:
                        self.assertLessEqual(math.hypot(x-1, y-1), .3100001)
                        self.assertGreater(math.hypot(x-1, y-1), .25)
                        self.assertGreater(z, 14 / 16)

    def test_artist_arm_clearance(self):
        root = Path(__file__).resolve().parents[2]
        model_root = root / 'common/src/main/resources/assets/ae2federation/models/block'
        for x in range(2):
            for y in range(2):
                model = json.loads((model_root / f'beam_former_large_{x}{y}.json').read_text())
                for element in model['elements']:
                    if element['name'] != 'corner':
                        continue
                    # Rotation is about the aperture centre, so it preserves radial distance.
                    nearest = []
                    for axis, offset in enumerate((x * 16, y * 16)):
                        lo, hi = element['from'][axis] + offset, element['to'][axis] + offset
                        nearest.append(max(lo - 16, 16 - hi, 0))
                    self.assertGreaterEqual(math.hypot(*nearest) / 16, .3125)

    def test_outer_clearance(self):
        from beam_halo import OUTER_INNER, OUTER_Z
        model_root = Path(__file__).resolve().parents[2] / 'common/src/main/resources/assets/ae2federation/models/block'
        arm_radius = top = 0
        for path in model_root.glob('beam_former_large_*.json'):
            x, y = map(int, path.stem[-2:])
            for element in json.loads(path.read_text())['elements']:
                points = list(itertools.product(*zip(element['from'], element['to'])))
                if r := element.get('rotation'):
                    points = [rotate(p, r['axis'], r['angle'], r['origin'], r.get('rescale', False)) for p in points]
                top = max(top, *(p[2] / 16 for p in points))
                if element['name'] == 'corner':
                    arm_radius = max(arm_radius, *(math.hypot(p[0]/16+x-1, p[1]/16+y-1) for p in points))
        self.assertAlmostEqual(arm_radius, math.sqrt(82)/16)
        self.assertGreater(OUTER_INNER - arm_radius, 1/16)
        self.assertGreater(OUTER_Z, top)
        for key in ('G', 'H', 'I'):
            for load in (0, .6, 1, 1.25):
                for frame in range(32):
                    for quad, alpha in generate(key, load, frame/32):
                        self.assertTrue(0 <= alpha <= 1)
                        if quad[0][2] == OUTER_Z:
                            # Polygon edges lie slightly inside the vertex radius.
                            self.assertGreater(min(math.hypot(x-1, y-1) for x,y,z in quad) * math.cos(math.pi/64), arm_radius+1/16)

    def test_retained_and_new_counts(self):
        self.assertEqual({k: 4*len(generate(k,.6,.25)) for k in CONCEPTS},
                         dict(BASE=0, B=960, D=192, F=192, G=1280, H=2240, I=256, J=5272, J2=5272))
        self.assertEqual(generate('H', .6, .25)[:240], generate('B', .6, .25))

    def test_inner_solid_clearance(self):
        # The actual b elements occupy +/-1 voxel across the cardinal axes,
        # from radial coordinate 4 to 6 voxels, z=13..17. All retained inlay
        # polygons stay in a convex quadrant strictly outside both strips.
        for key in ('B', 'D', 'F', 'H'):
            for load in (0, .6, 1, 1.25):
                for frame in range(32):
                    for quad, _ in generate(key, load, frame/32):
                        if quad[0][2] < 1:
                            for axis in (0, 1):
                                values = [v[axis]-1 for v in quad]
                                self.assertTrue(min(values) > 1/16 or max(values) < -1/16)

    def test_no_artist_solid_intersection(self):
        model_root = Path(__file__).resolve().parents[2] / 'common/src/main/resources/assets/ae2federation/models/block'
        solids = []
        for path in model_root.glob('beam_former_large_*.json'):
            ox, oy = map(int, path.stem[-2:])
            for element in json.loads(path.read_text())['elements']:
                lo, hi = element['from'], element['to']
                face = [(lo[0],lo[1],lo[2]), (hi[0],lo[1],lo[2]),
                        (hi[0],hi[1],lo[2]), (lo[0],hi[1],lo[2])]
                if r := element.get('rotation'):
                    self.assertEqual(r['axis'], 'z')
                    face = [rotate(p,r['axis'],r['angle'],r['origin'],r.get('rescale',False)) for p in face]
                solids.append(([(p[0]/16+ox,p[1]/16+oy) for p in face],lo[2]/16,hi[2]/16))

        def overlaps(a, b):
            for polygon in (a, b):
                for p, q in zip(polygon,polygon[1:]+polygon[:1]):
                    nx, ny = p[1]-q[1], q[0]-p[0]
                    if abs(nx)+abs(ny) < 1e-12:
                        continue
                    pa = [x*nx+y*ny for x,y in a]
                    pb = [x*nx+y*ny for x,y in b]
                    if max(pa) < min(pb)-1e-10 or max(pb) < min(pa)-1e-10:
                        return False
            return True

        # Portal inlays deliberately continue behind opaque artist clamps.
        for key in ('BASE', 'B', 'D', 'F', 'G', 'H', 'I'):
            for load in (0,.6,1,1.25):
                for phase in (0,.25,.5,.75):
                    for quad, _ in generate(key,load,phase):
                        z = quad[0][2]
                        for face, lower, upper in solids:
                            if lower <= z <= upper:
                                self.assertFalse(overlaps([(p[0],p[1]) for p in quad],face),
                                                 (key,load,phase,quad,face))

    def test_cycle_and_counts(self):
        for key in CONCEPTS:
            first = generate(key, .6, 0)
            self.assertEqual(len(first), len(generate(key, .6, .5)))
            for (qa, aa), (qb, ab) in zip(first, generate(key, .6, 1)):
                self.assertAlmostEqual(aa, ab)
                for a, b in zip(qa, qb):
                    for x, y in zip(a, b):
                        self.assertAlmostEqual(x, y)

    def test_portal_planes_materials_and_motion(self):
        for key in ('J', 'J2'):
            mesh = portal(key, .6, .25)
            self.assertEqual(len(mesh.faces) * 4, 5272)
            self.assertEqual(portal(key, .6, 0, False).faces, [])
            for face, index in zip(mesh.faces, mesh.indices):
                name, rgb, alpha, mode = mesh.materials[index]
                self.assertTrue(0 <= alpha <= 1)
                for i in face:
                    x, y, z = mesh.vertices[i]
                    self.assertLessEqual(math.hypot(x-1, y-1), 9.800001/16)
                    if name in ('Depth disc', 'Inward streak'):
                        self.assertLess(z, 1)  # Behind the original beam start.
                    elif key == 'J':
                        self.assertLess(z, 14/16)  # Actual diagonal arm occlusion.
                    else:
                        self.assertAlmostEqual(z, 19/16, places=3)
                self.assertEqual(mode, 'solid' if name == 'Depth disc' else 'add')
            self.assertNotEqual(generate(key, .6, 0), generate(key, .6, .125))
            alarm = portal(key, 1.25, .25)
            for name, rgb, alpha, mode in alarm.materials:
                if name == 'White core':
                    self.assertGreater(rgb[0], rgb[2])

    def test_load_and_alarm(self):
        self.assertNotEqual(generate('D', 0, .25), generate('D', 1, .25))
        self.assertGreater(palette(.6)[2], palette(.6)[0])
        self.assertGreater(palette(1.25)[0], palette(1.25)[2])
        for key in ('D', 'I'):
            self.assertNotEqual(generate(key, .6, 0), generate(key, .6, .125))


if __name__ == '__main__':
    unittest.main()
