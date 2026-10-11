"""Checks against real repository assets and production Java exports; no dummy fixtures."""
import json
import math
from pathlib import Path
import tempfile
import unittest

from assets import Assets, animation_schedule, dependency_packs, model_quads

ROOT=Path(__file__).resolve().parents[2]


class AssetTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.assets=Assets(ROOT,Path(self.temp.name),dependency_packs(ROOT))

    def test_every_actual_model_resolves_and_has_real_texture_files(self):
        count=0
        for path in sorted((self.assets.root/'ae2federation/models').rglob('*.json')):
            identifier='ae2federation:'+str(path.relative_to(self.assets.root/'ae2federation/models').with_suffix(''))
            model=self.assets.model(identifier)
            for quad in model_quads(self.assets,model):
                texture,_=self.assets.texture(quad['texture'])
                self.assertTrue(texture.read_bytes().startswith(b'\x89PNG'))
                self.assertEqual(4,len(quad['uv']))
                count+=1
        self.assertGreater(count,500)
        self.assertEqual(4,len(self.assets.omitted_internal_faces))

    def test_parent_geometry_and_child_texture_override_for_actual_p2p(self):
        model=self.assets.model('ae2federation:part/p2p_tunnel_federation')
        quads=model_quads(self.assets,model)
        self.assertGreater(len(quads),6)
        self.assertIn('ae2federation:part/p2p_tunnel_federation',{q['texture'] for q in quads})
        self.assertIn('ae2:part/p2p_tunnel_sides',{q['texture'] for q in quads})

    def test_provider_facing_transforms_keep_front_on_requested_side(self):
        model=self.assets.model('ae2federation:block/pattern_provider')
        raw=model_quads(self.assets,model)
        variants=self.assets.json('assets/ae2federation/blockstates/pattern_provider.json')['variants']
        from assets import rotate
        # The authored +Z front must become the blockstate's requested direction.
        expected={'south':(0,0,1),'north':(0,0,-1),'west':(-1,0,0),'east':(1,0,0),'up':(0,1,0),'down':(0,-1,0)}
        for key,variant in variants.items():
            point=[8,8,16]
            for axis in ('x','y'):
                point=rotate(point,axis,-variant.get(axis,0))
            actual=tuple(round((v-8)/8) for v in point)
            self.assertEqual(expected[key.split('=')[1]],actual)
            converted=model_quads(self.assets,model,variant)
            self.assertEqual([q['uv'] for q in raw],[q['uv'] for q in converted])

    def test_real_item_animation_preserves_order_hold_and_interpolation_metadata(self):
        _,metadata=self.assets.texture('ae2federation:item/nexus_processor')
        fw,fh,sequence=animation_schedule(metadata,16,256)
        self.assertEqual((16,16),(fw,fh))
        self.assertEqual((0,40),sequence[-1])
        self.assertEqual((1,2),sequence[-2])
        self.assertTrue(metadata['interpolate'])

    def test_missing_external_assets_fail_instead_of_substitution(self):
        assets=Assets(ROOT,Path(self.temp.name))
        with self.assertRaises(FileNotFoundError):
            assets.model('ae2federation:part/p2p_tunnel_federation')


class CableExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=json.loads((ROOT/'tools/blender/build/cable.json').read_text())

    def test_all_flow_masks_reach_exactly_the_connected_boundaries(self):
        self.assertEqual(64,len(self.data['flow']))
        for mask,ribbons in enumerate(self.data['flow']):
            boundary=0
            for ribbon in ribbons:
                for vertex in ribbon['vertices']:
                    for axis,value in enumerate(vertex[:3]):
                        self.assertGreaterEqual(value,0); self.assertLessEqual(value,1)
                        if value==1: boundary |= 1<<(axis*2)
                        if value==0: boundary |= 2<<(axis*2)
            self.assertEqual(mask,boundary)

    def test_actual_phase_preserves_seams_across_negative_coordinates(self):
        for phase in self.data['phases']:
            for x in range(-5,5):
                difference=phase[x%4]+.25-phase[(x+1)%4]
                self.assertAlmostEqual(round(difference),difference,places=6)

    def test_straight_shell_has_four_sides_and_no_end_caps(self):
        for mask in (3,12,48):
            connections=sum(3<<(bit*2) for bit in range(6) if mask&(1<<bit))
            quads=self.data['shells'][str(connections)]
            self.assertEqual(4,len(quads))
            axis={3:0,12:1,48:2}[mask]
            for q in quads:
                self.assertEqual({0,1},{p[axis] for p in q['positions']})
                self.assertEqual('ae2federation:part/cable/dense/line',q['texture'])

    def test_captured_uvs_and_bounds_are_finite_for_every_export(self):
        for quads in self.data['shells'].values():
            for q in quads:
                self.assertEqual(4,len(q['positions']))
                for p in q['positions']:
                    self.assertTrue(all(math.isfinite(v) and 0<=v<=1 for v in p))
                self.assertTrue(all(math.isfinite(v) for uv in q['uv'] for v in uv))

    def test_connected_core_uses_artist_variants(self):
        textures={q['texture'] for quads in self.data['shells'].values() for q in quads}
        for name in ('core','core_connected_1','core_connected_2','core_connected_2_straight','core_connected_3','core_connected_4'):
            self.assertIn('ae2federation:part/cable/dense/'+name,textures)


if __name__=='__main__':
    unittest.main()
