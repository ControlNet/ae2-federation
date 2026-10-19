"""Visual load response tests. Ratios are explicit preview inputs, not telemetry."""
import unittest

from beam_load import (response, smooth_load, palette_for_load, emission_for_load,
                       generate_for_load)


class BeamLoadTests(unittest.TestCase):
    def test_normal_load_strength_is_monotonic_and_full_is_not_overload(self):
        values = [response(r) for r in (0, .25, .6, .9, 1)]
        for field in ('core_radius','pulse_density','pulse_amplitude','halo_radius','emission'):
            sequence = [getattr(v,field) for v in values]
            self.assertEqual(sequence, sorted(sequence), field)
        self.assertTrue(all(not v.overloaded for v in values))
        self.assertTrue(response(1.001).overloaded)
        self.assertEqual(response(10).core_radius,response(1).core_radius)

    def test_overload_uses_red_shoulders_but_never_blackouts(self):
        self.assertGreater(palette_for_load(1)[1][2],palette_for_load(1)[1][0])
        self.assertGreater(palette_for_load(1.25)[1][0],palette_for_load(1.25)[1][2])
        levels=[emission_for_load(1.25,i/32) for i in range(33)]
        self.assertGreaterEqual(min(levels),1.39)
        self.assertLessEqual(max(levels),2.21)
        self.assertAlmostEqual(levels[0],levels[-1])

    def test_smoothing_does_not_depend_on_frame_rate(self):
        for start,target in [(0,1.25),(1.25,0)]:
            result=[]
            for fps in (20,60,144):
                current=start
                for _ in range(fps):current=smooth_load(current,target,1/fps)
                result.append(current)
            self.assertAlmostEqual(result[0],result[1])
            self.assertAlmostEqual(result[0],result[2])

    def test_load_changes_preserve_mesh_topology_and_fixed_shell_envelope(self):
        initial=generate_for_load(0,3,(0,0,0),(0,0,0),0)
        for ratio in (.25,.6,.9,1,1.25,100):
            mesh=generate_for_load(ratio,3,(0,0,0),(0,0,0),.25)
            self.assertEqual(initial.faces,mesh.faces)
            self.assertEqual(initial.materials,mesh.materials)
            for x,y,z in mesh.vertices:
                self.assertTrue(0 <= x <= 1)
                self.assertLessEqual((y+.5)**2+(z-.5)**2,.08**2)

    def test_invalid_ratios_are_rejected(self):
        for ratio in (-1,float('nan'),float('inf')):
            with self.assertRaises(ValueError):response(ratio)


if __name__=='__main__':
    unittest.main()
