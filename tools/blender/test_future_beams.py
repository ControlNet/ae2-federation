"""Checks of the actual procedural beam generators, without Blender or dummy assets."""
import math
import unittest

from future_beams import PALETTE, PROFILES, generate


class FutureBeamTests(unittest.TestCase):
    def test_eight_distinct_designs_have_valid_geometry(self):
        self.assertEqual(8,len(PROFILES))
        signatures=set()
        for key in PROFILES:
            mesh=generate(key,3,(-3,0,0),(0,0,0),.25)
            self.assertTrue(mesh.vertices,key)
            self.assertEqual(len(mesh.faces),len(mesh.materials))
            self.assertTrue(all(math.isfinite(v) for point in mesh.vertices for v in point))
            self.assertTrue(all(0<=i<len(mesh.vertices) for face in mesh.faces for i in face))
            self.assertTrue(all(0<=i<len(PALETTE) for i in mesh.materials))
            signatures.add(tuple(mesh.vertices))
        self.assertEqual(8,len(signatures))

    def test_animation_preserves_topology(self):
        for key in PROFILES:
            for mask in (3,12,48,19,63):
                first=generate(key,mask,(0,0,0),(0,0,0),0)
                for phase in (.125,.5,.9375):
                    later=generate(key,mask,(0,0,0),(0,0,0),phase)
                    self.assertEqual(first.faces,later.faces,(key,mask,phase))
                    self.assertEqual(first.materials,later.materials)
                    self.assertEqual(len(first.vertices),len(later.vertices))

    def test_one_cycle_returns_to_the_same_pose(self):
        for key in PROFILES:
            first=generate(key,19,(-2,0,0),(0,0,0),0)
            last=generate(key,19,(-2,0,0),(0,0,0),1)
            self.assertLess(max(abs(a-b) for p,q in zip(first.vertices,last.vertices) for a,b in zip(p,q)),1e-9,key)

    def test_straight_profiles_fit_inside_fixed_shell_cross_section(self):
        # Approved dense-machine arms have a 10/16 width. All concepts stay well inside it.
        for key in PROFILES:
            for phase in (0,.25,.5,.75):
                mesh=generate(key,3,(0,0,0),(0,0,0),phase)
                for x,y,z in mesh.vertices:
                    self.assertLessEqual(abs(-y-.5),.21,key)
                    self.assertLessEqual(abs(z-.5),.21,key)
                    # Crystals/fins may straddle an adjacent connected block boundary.
                    self.assertGreaterEqual(x,-.13,key)
                    self.assertLessEqual(x,1.13,key)


if __name__=='__main__':
    unittest.main()
